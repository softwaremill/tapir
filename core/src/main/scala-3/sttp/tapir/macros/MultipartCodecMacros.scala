package sttp.tapir.macros

import sttp.tapir.{AnyPart, Codec, CodecFormat, DecodeResult, FileRange, MultipartCodec, PartCodec, RawBodyType, Schema}
import sttp.tapir.generic.Configuration
import sttp.tapir.internal.{CaseClass, CaseClassField}
import sttp.model.Part

import scala.annotation.tailrec
import scala.quoted.*
import java.nio.charset.StandardCharsets

trait MultipartCodecMacros {
  inline given multipartCaseClassCodec[T](using inline c: Configuration): MultipartCodec[T] =
    ${ MultipartCodecMacros.multipartCaseClassCodecImpl[T]('c) }
}

object MultipartCodecMacros {
  def multipartCaseClassCodecImpl[T: Type](conf: Expr[Configuration])(using q: Quotes): Expr[MultipartCodec[T]] = {
    import quotes.reflect.*
    val caseClass = new CaseClass[q.type, T](using summon[Type[T]], q)
    val encodedNameAnnotationSymbol = TypeTree.of[Schema.annotations.encodedName].tpe.typeSymbol

    def summonPartCodec[f: Type](field: CaseClassField[q.type, T]) = {
      val candidates = List(
        () =>
          Expr
            .summon[Codec[List[String], f, CodecFormat.TextPlain]]
            .map(c => '{ PartCodec(RawBodyType.StringBody(StandardCharsets.UTF_8), $c) }),
        () =>
          Expr
            .summon[Codec[List[String], f, _ <: CodecFormat]]
            .map(c => '{ PartCodec(RawBodyType.StringBody(StandardCharsets.UTF_8), $c) }),
        () => Expr.summon[Codec[List[Array[Byte]], f, _ <: CodecFormat]].map(c => '{ PartCodec(RawBodyType.ByteArrayBody, $c) }),
        () => Expr.summon[Codec[List[java.io.InputStream], f, _ <: CodecFormat]].map(c => '{ PartCodec(RawBodyType.InputStreamBody, $c) }),
        () => Expr.summon[Codec[List[java.nio.ByteBuffer], f, _ <: CodecFormat]].map(c => '{ PartCodec(RawBodyType.ByteBufferBody, $c) }),
        () => Expr.summon[Codec[List[FileRange], f, _ <: CodecFormat]].map(c => '{ PartCodec(RawBodyType.FileBody, $c) })
      )

      @tailrec
      def firstNotEmpty(c: List[() => Option[Expr[PartCodec[_, _]]]]): Expr[PartCodec[_, _]] = c match {
        case Nil =>
          report.throwError(s"Cannot find a codec between a List[T] for some basic type T and: ${field.tpe}")
        case h :: t =>
          h() match {
            case None    => firstNotEmpty(t)
            case Some(e) => e
          }
      }

      firstNotEmpty(candidates)
    }

    def fieldTransformedName(field: CaseClassField[q.type, T]): Expr[String] = {
      val encodedName = field.extractStringArgFromAnnotation(encodedNameAnnotationSymbol)
      '{ ${ Expr(encodedName) }.getOrElse($conf.toEncodedName(${ Expr(field.name) })) }
    }

    val partCodecs: Expr[Map[String, PartCodec[_, _]]] = {
      val partCodecPairs = caseClass.fields.map { field =>
        val partCodec = field.tpe.asType match
          case '[Part[f]] => summonPartCodec[f](field)
          case '[f]       => summonPartCodec[f](field)

        '{ ${ fieldTransformedName(field) } -> $partCodec }
      }

      '{ Map(${ Varargs(partCodecPairs) }: _*) }
    }

    def termMethodByNameUnsafe(term: Term, name: String): Symbol = term.tpe.typeSymbol.memberMethod(name).head
    def getter(term: Term, name: String, skipArgList: Boolean = false): Term = {
      val base = term.select(termMethodByNameUnsafe(term, name))
      if (skipArgList) base else base.appliedToNone
    }

    def encodeDefBody(tTerm: Term): Term = {
      val fieldsEncode = caseClass.fields.map { field =>
        val fieldEncode: Expr[AnyPart] = field.tpe.asType match
          case '[Part[f]] =>
            '{ ${ tTerm.select(field.symbol).asExprOf[Part[f]] }.copy(name = ${ fieldTransformedName(field) }) }
          case '[f] =>
            val fieldVal = tTerm.select(field.symbol).asExprOf[f]
            val base = '{ Part(${ fieldTransformedName(field) }, $fieldVal) }

            // if the field is a File/Path, and is not wrapped in a Part, during encoding adding the file's name
            val fieldTypeName = field.tpe.dealias.show
            if fieldTypeName.startsWith("java.io.File") then {
              '{ $base.fileName(${ getter(fieldVal.asTerm, "getName").asExprOf[String] }) }
            } else if fieldTypeName.startsWith("java.nio.Path") then
              '{ $base.fileName(${ getter(getter(fieldVal.asTerm, "toFile"), "getName").asExprOf[String] }) }
            else if fieldTypeName.startsWith("org.scalajs.dom.File") then
              '{ $base.fileName(${ getter(fieldVal.asTerm, "name", skipArgList = true).asExprOf[String] }) }
            else base

        fieldEncode
      }

      '{ List(${ Varargs(fieldsEncode) }: _*) }.asTerm
    }

    val encodeDefSymbol =
      Symbol.newMethod(Symbol.spliceOwner, "encode", MethodType(List("t"))(_ => List(caseClass.tpe), _ => TypeRepr.of[Seq[AnyPart]]))
    val encodeDef = DefDef(
      encodeDefSymbol,
      { case List(List(tTerm: Term)) =>
        Some(encodeDefBody(tTerm).changeOwner(encodeDefSymbol))
      }
    )
    val encodeExpr = Block(List(encodeDef), Closure(Ref(encodeDefSymbol), None)).asExprOf[T => Seq[AnyPart]]

    //

    def decodeDefBody(partsTerm: Term): Term = {
      def fieldsDecode(partsMap: Expr[Map[String, Part[_]]]) = caseClass.fields.map { field =>
        field.tpe.asType match
          case '[Part[f]] => '{ $partsMap(${ fieldTransformedName(field) }) }
          case '[f]       => '{ $partsMap(${ fieldTransformedName(field) }).body }
      }

      '{
        val partsMap: Map[String, AnyPart] = ${ partsTerm.asExprOf[Seq[AnyPart]] }.map(p => p.name -> p).toMap
        val values = List(${ Varargs(fieldsDecode('partsMap)) }: _*)
        ${ caseClass.instanceFromValues('{ values }) }
      }.asTerm
    }

    val decodeDefSymbol =
      Symbol.newMethod(Symbol.spliceOwner, "decode", MethodType(List("params"))(_ => List(TypeRepr.of[Seq[AnyPart]]), _ => TypeRepr.of[T]))
    val decodeDef = DefDef(
      decodeDefSymbol,
      { case List(List(paramsTerm: Term)) =>
        Some(decodeDefBody(paramsTerm).changeOwner(decodeDefSymbol))
      }
    )
    val decodeExpr = Block(List(decodeDef), Closure(Ref(decodeDefSymbol), None)).asExprOf[Seq[AnyPart] => T]

    '{
      Codec
        .multipartCodec($partCodecs, None)
        .map($decodeExpr)($encodeExpr)
        .schema(${ Expr.summon[Schema[T]].getOrElse(report.throwError(s"Cannot find a given Schema[${summon[Type[T]]}].")) })
    }
  }
}
