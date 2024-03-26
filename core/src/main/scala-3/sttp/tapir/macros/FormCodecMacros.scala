package sttp.tapir.macros

import sttp.tapir.generic.Configuration
import sttp.tapir.internal.{CaseClass, CaseClassField}
import sttp.tapir.{Codec, CodecFormat, DecodeResult, Schema}

import scala.quoted.*

trait FormCodecMacros {
  inline given formCaseClassCodec[T](using inline c: Configuration): Codec[String, T, CodecFormat.XWwwFormUrlencoded] =
    ${ FormCodecMacros.formCaseClassCodecImpl[T]('c) }
}

private[tapir] object FormCodecMacros {
  def formCaseClassCodecImpl[T: Type](
      conf: Expr[Configuration]
  )(using q: Quotes): Expr[Codec[String, T, CodecFormat.XWwwFormUrlencoded]] = {
    import quotes.reflect.*
    val caseClass = new CaseClass[q.type, T](using summon[Type[T]], q)
    val encodedNameAnnotationSymbol = TypeTree.of[Schema.annotations.encodedName].tpe.typeSymbol

    def summonCodec[f: Type](field: CaseClassField[q.type, T]) = Expr.summon[Codec[List[String], f, CodecFormat.TextPlain]].getOrElse {
      report.errorAndAbort(s"Cannot find Codec[List[String], T, CodecFormat.TextPlain]] for field: ${field}, of type: ${field.tpe}")
    }

    def encodeDefBody(tTerm: Term): Term = {
      val fieldsEncode = caseClass.fields.map { field =>
        val encodedName = field.extractStringArgFromAnnotation(encodedNameAnnotationSymbol)
        val fieldEncode: Expr[List[(String, String)]] = field.tpe.asType match
          case '[f] =>
            val codec = summonCodec[f](field)

            '{
              val transformedName: String = ${ Expr(encodedName) }.getOrElse($conf.toEncodedName(${ Expr(field.name) }))
              $codec.encode(${ tTerm.select(field.symbol).asExprOf[f] }).map(v => (transformedName, v))
            }

        fieldEncode
      }

      '{ List(${ Varargs(fieldsEncode) }: _*).flatten }.asTerm
    }

    val encodeDefSymbol = Symbol.newMethod(
      Symbol.spliceOwner,
      "encode",
      MethodType(List("t"))(_ => List(caseClass.tpe), _ => TypeRepr.of[Seq[(String, String)]])
    )
    val encodeDef = DefDef(
      encodeDefSymbol,
      { case List(List(tTerm: Term)) =>
        Some(encodeDefBody(tTerm).changeOwner(encodeDefSymbol))
      }
    )
    val encodeExpr = Block(List(encodeDef), Closure(Ref(encodeDefSymbol), None)).asExprOf[T => Seq[(String, String)]]

    //

    def decodeDefBody(paramsTerm: Term): Term = {
      def fieldsDecode(paramsMap: Expr[Map[String, Seq[String]]]) = caseClass.fields.map { field =>
        val encodedName = field.extractStringArgFromAnnotation(encodedNameAnnotationSymbol)
        val fieldDecode = field.tpe.asType match
          case '[f] =>
            val codec = summonCodec[f](field)

            '{
              val transformedName = ${ Expr(encodedName) }.getOrElse($conf.toEncodedName(${ Expr(field.name) }))
              $codec.decode($paramsMap.get(transformedName).toList.flatten)
            }

        fieldDecode
      }

      '{
        val paramsMap: Map[String, Seq[String]] =
          ${ paramsTerm.asExprOf[Seq[(String, String)]] }.groupBy(_._1).transform((_, v) => v.map(_._2))
        val decodeResults = List(${ Varargs(fieldsDecode('paramsMap)) }: _*)
        DecodeResult.sequence(decodeResults).map(values => ${ caseClass.instanceFromValues('{ values }) })
      }.asTerm
    }

    val decodeDefSymbol = Symbol.newMethod(
      Symbol.spliceOwner,
      "decode",
      MethodType(List("params"))(_ => List(TypeRepr.of[Seq[(String, String)]]), _ => TypeRepr.of[DecodeResult[T]])
    )
    val decodeDef = DefDef(
      decodeDefSymbol,
      { case List(List(paramsTerm: Term)) =>
        Some(decodeDefBody(paramsTerm).changeOwner(decodeDefSymbol))
      }
    )
    val decodeExpr = Block(List(decodeDef), Closure(Ref(decodeDefSymbol), None)).asExprOf[Seq[(String, String)] => DecodeResult[T]]

    '{
      Codec.formSeqUtf8
        .mapDecode($decodeExpr)($encodeExpr)
        .schema(${ Expr.summon[Schema[T]].getOrElse(report.errorAndAbort(s"Cannot find a given Schema[${Type.show[T]}].")) })
    }
  }
}
