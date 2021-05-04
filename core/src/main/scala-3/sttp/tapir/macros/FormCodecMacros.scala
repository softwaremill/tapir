package sttp.tapir.macros

import sttp.tapir.generic.Configuration
import sttp.tapir.{Codec, CodecFormat, DecodeResult, encodedName, Schema}

import scala.compiletime.constValue
import scala.compiletime.erasedValue
import scala.compiletime.summonFrom
import scala.compiletime.summonInline
import scala.compiletime.summonAll
import scala.deriving.Mirror
import scala.quoted.*

trait FormCodecMacros {
  implicit inline def formCaseClassCodec[T](using inline c: Configuration): Codec[String, T, CodecFormat.XWwwFormUrlencoded] =
    ${FormCodecMacros.formCaseClassCodecImpl[T]('c)}
}

class CaseClass[Q <: Quotes, T: Type](using val q: Q) {
  import q.reflect.*

  val tpe = TypeRepr.of[T]
  val symbol = tpe.typeSymbol

  if !symbol.flags.is(Flags.Case) then
    report.throwError(s"Form codec can only be derived for case classes, but got: ${summon[Type[T]]}")

  def fields: List[CaseClassField[Q, T]] =
    symbol.caseFields.zip(symbol.primaryConstructor.paramSymss.head).map { (caseField, constructorField) =>
      new CaseClassField[Q, T](using q, summon[Type[T]])(caseField, constructorField, tpe.memberType(caseField))
    }
}

class CaseClassField[Q <: Quotes, T](using val q: Q, t: Type[T])(val symbol: q.reflect.Symbol, constructorField: q.reflect.Symbol, val tpe: q.reflect.TypeRepr) {
  import q.reflect.*

  def name = symbol.name

  def extractArgFromAnnotation(annSymbol: Symbol): Option[String] = constructorField.getAnnotation(annSymbol) map {
    case Apply(_, List(Literal(c: Constant))) if c.value.isInstanceOf[String] => c.value.asInstanceOf[String]
    case _ => report.throwError(s"Cannot extract annotation: $annSymbol, from field: $symbol, of type: ${summon[Type[T]]}")
  }
}

object FormCodecMacros {

  def formCaseClassCodecImpl[T: Type](conf: Expr[Configuration])(using q: Quotes): Expr[Codec[String, T, CodecFormat.XWwwFormUrlencoded]] = {
    import quotes.reflect.*
    val caseClass = new CaseClass[q.type, T](using summon[Type[T]], q)
    val encodedNameAnnotationSymbol = TypeTree.of[encodedName].tpe.typeSymbol

    def encodeDefBody(tTerm: Term): Term = {
      val fieldsEncode = caseClass.fields.map { field =>
        val encodedName = field.extractArgFromAnnotation(encodedNameAnnotationSymbol)
        val fieldEncode: Expr[List[(String, String)]] = field.tpe.asType match
          case '[f] => val codec = Expr.summon[Codec[List[String], f, CodecFormat.TextPlain]].getOrElse {
              report.throwError(s"Cannot find Codec[List[String], T, CodecFormat.TextPlain]] for field: ${field}, of type: ${field.tpe}")
            }

            '{
              val transformedName: String = ${Expr(encodedName)}.getOrElse($conf.toEncodedName(${Expr(field.name)}))
              $codec.encode(${tTerm.select(field.symbol).asExprOf[f]}).map(v => (transformedName, v))
            }

        fieldEncode
      }

      '{List(${Varargs(fieldsEncode)}: _*).flatten}.asTerm
    }

    val encodeDefSymbol = Symbol.newMethod(Symbol.spliceOwner, "encode", MethodType(List("t"))(_ => List(caseClass.tpe), _ => TypeRepr.of[Seq[(String, String)]]))
    val encodeDef = DefDef(
      encodeDefSymbol, {
        case List(List(tTerm: Term)) => Some(encodeDefBody(tTerm).changeOwner(encodeDefSymbol))
      }
    )
    val encodeExpr = Block(List(encodeDef), Closure(Ref(encodeDefSymbol), None)).asExprOf[T => Seq[(String, String)]]

    //

    def decodeDefBody(paramsTerm: Term): Term = {
      def fieldsDecode(paramsMap: Expr[Map[String, Seq[String]]]) = caseClass.fields.map { field =>
        val encodedName = field.extractArgFromAnnotation(encodedNameAnnotationSymbol)
        val fieldDecode = field.tpe.asType match
          case '[f] => val codec = Expr.summon[Codec[List[String], f, CodecFormat.TextPlain]].getOrElse {
              report.throwError(s"Cannot find Codec[List[String], T, CodecFormat.TextPlain]] for field: ${field}, of type: ${field.tpe}")
            }

            '{
              val transformedName = ${Expr(encodedName)}.getOrElse($conf.toEncodedName(${Expr(field.name)}))
              $codec.decode($paramsMap.get(transformedName).toList.flatten)
            }

        fieldDecode
      }

      '{
        val paramsMap: Map[String, Seq[String]] = ${paramsTerm.asExprOf[Seq[(String, String)]]}.groupBy(_._1).transform((_, v) => v.map(_._2))
        val decodeResults = List(${Varargs(fieldsDecode('paramsMap))}: _*)
        DecodeResult.sequence(decodeResults).map { values =>
          ${
            Apply(
              Select.unique(New(Inferred(caseClass.tpe)), "<init>"),
              caseClass.symbol.caseFields.zipWithIndex.map { (field: Symbol, i: Int) =>
                TypeApply(
                  Select.unique('{values.apply(${Expr(i)})}.asTerm, "asInstanceOf"),
                  List(Inferred(caseClass.tpe.memberType(field)))
                )
              }
            ).asExprOf[T]
          }
        }
      }.asTerm
    }

    val decodeDefSymbol = Symbol.newMethod(Symbol.spliceOwner, "decode", MethodType(List("params"))(_ => List(TypeRepr.of[Seq[(String, String)]]), _ => TypeRepr.of[DecodeResult[T]]))
    val decodeDef = DefDef(
      decodeDefSymbol, {
        case List(List(paramsTerm: Term)) => Some(decodeDefBody(paramsTerm).changeOwner(decodeDefSymbol))
      }
    )
    val decodeExpr = Block(List(decodeDef), Closure(Ref(decodeDefSymbol), None)).asExprOf[Seq[(String, String)] => DecodeResult[T]]

    '{
      Codec.formSeqCodecUtf8
        .mapDecode($decodeExpr)($encodeExpr)
        //TODO .schema(${Expr.summon[Schema[T]].getOrElse(report.throwError(s"Cannot find a given Schema[${summon[Type[T]]}]."))})
    }
  }
}
