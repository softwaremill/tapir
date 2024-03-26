package sttp.tapir.macros

import sttp.tapir.{Codec, CodecFormat, FileRange, MultipartCodec, PartCodec, RawBodyType, Schema}
import sttp.tapir.generic.Configuration
import sttp.tapir.internal.{CaseClass, CaseClassField}
import sttp.model.Part

import scala.annotation.tailrec
import scala.collection.immutable.ListMap
import scala.quoted.*
import java.nio.charset.StandardCharsets

trait MultipartCodecMacros {
  inline given multipartCaseClassCodec[T](using inline c: Configuration): MultipartCodec[T] =
    ${ MultipartCodecMacros.multipartCaseClassCodecImpl[T]('c) }
}

private[tapir] object MultipartCodecMacros {
  def multipartCaseClassCodecImpl[T: Type](conf: Expr[Configuration])(using q: Quotes): Expr[MultipartCodec[T]] = {
    import quotes.reflect.*
    val caseClass = new CaseClass[q.type, T](using summon[Type[T]], q)
    val encodedNameAnnotationSymbol = TypeTree.of[Schema.annotations.encodedName].tpe.typeSymbol

    def summonPartCodec[f: Type](field: CaseClassField[q.type, T]) = {
      val candidates = List(
        () =>
          Expr
            .summon[Codec[List[Part[String]], f, CodecFormat.TextPlain]]
            .map(c => '{ PartCodec(RawBodyType.StringBody(StandardCharsets.UTF_8), $c) }),
        () =>
          Expr
            .summon[Codec[List[Part[String]], f, _ <: CodecFormat]]
            .map(c => '{ PartCodec(RawBodyType.StringBody(StandardCharsets.UTF_8), $c) }),
        () => Expr.summon[Codec[List[Part[Array[Byte]]], f, _ <: CodecFormat]].map(c => '{ PartCodec(RawBodyType.ByteArrayBody, $c) }),
        () =>
          Expr
            .summon[Codec[List[Part[java.io.InputStream]], f, _ <: CodecFormat]]
            .map(c => '{ PartCodec(RawBodyType.InputStreamBody, $c) }),
        () =>
          Expr.summon[Codec[List[Part[java.nio.ByteBuffer]], f, _ <: CodecFormat]].map(c => '{ PartCodec(RawBodyType.ByteBufferBody, $c) }),
        () => Expr.summon[Codec[List[Part[FileRange]], f, _ <: CodecFormat]].map(c => '{ PartCodec(RawBodyType.FileBody, $c) })
      )

      @tailrec
      def firstNotEmpty(c: List[() => Option[Expr[PartCodec[_, _]]]]): Expr[PartCodec[_, _]] = c match {
        case Nil =>
          report.errorAndAbort(s"Cannot find a codec between a List[Part[T]] for some basic type T and: ${field.tpe}")
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

    val partCodecs: Expr[ListMap[String, PartCodec[_, _]]] = {
      val partCodecPairs = caseClass.fields.map { field =>
        val partCodec = field.tpe.asType match
          case '[f] => summonPartCodec[f](field)

        '{ ${ fieldTransformedName(field) } -> $partCodec }
      }

      '{ ListMap(${ Varargs(partCodecPairs) }: _*) }
    }

    def encodeDefBody(tTerm: Term): Term = {
      val fieldsEncode = caseClass.fields.map { field =>
        val fieldValue = field.tpe.asType match
          case '[f] =>
            tTerm.select(field.symbol).asExprOf[f]
        '{ ${ fieldTransformedName(field) } -> $fieldValue }
      }

      '{ ListMap(${ Varargs(fieldsEncode) }: _*) }.asTerm
    }

    val encodeDefSymbol =
      Symbol.newMethod(
        Symbol.spliceOwner,
        "encode",
        MethodType(List("t"))(_ => List(caseClass.tpe), _ => TypeRepr.of[ListMap[String, Any]])
      )
    val encodeDef = DefDef(
      encodeDefSymbol,
      { case List(List(tTerm: Term)) =>
        Some(encodeDefBody(tTerm).changeOwner(encodeDefSymbol))
      }
    )
    val encodeExpr = Block(List(encodeDef), Closure(Ref(encodeDefSymbol), None)).asExprOf[T => ListMap[String, Any]]

    //

    def decodeDefBody(partsTerm: Term): Term = {
      def fieldsDecode(partsMap: Expr[ListMap[String, Any]]) = caseClass.fields.map { field =>
        '{ $partsMap(${ fieldTransformedName(field) }) }
      }

      '{
        val partsMap: ListMap[String, Any] = ${ partsTerm.asExprOf[ListMap[String, Any]] }
        val values = List(${ Varargs(fieldsDecode('partsMap)) }: _*)
        ${ caseClass.instanceFromValues('{ values }) }
      }.asTerm
    }

    val decodeDefSymbol =
      Symbol.newMethod(
        Symbol.spliceOwner,
        "decode",
        MethodType(List("params"))(_ => List(TypeRepr.of[ListMap[String, Any]]), _ => TypeRepr.of[T])
      )
    val decodeDef = DefDef(
      decodeDefSymbol,
      { case List(List(paramsTerm: Term)) =>
        Some(decodeDefBody(paramsTerm).changeOwner(decodeDefSymbol))
      }
    )
    val decodeExpr = Block(List(decodeDef), Closure(Ref(decodeDefSymbol), None)).asExprOf[ListMap[String, Any] => T]

    '{
      Codec
        .multipart($partCodecs, None)
        .map($decodeExpr)($encodeExpr)
        .schema(${ Expr.summon[Schema[T]].getOrElse(report.errorAndAbort(s"Cannot find a given Schema[${Type.show[T]}].")) })
    }
  }
}
