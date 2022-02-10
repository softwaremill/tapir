package sttp.tapir.internal

import sttp.tapir.Codec
import sttp.tapir.CodecFormat.TextPlain

import scala.quoted.*

object CodecValueClassMacro {

  inline def derivedValueClass[T <: AnyVal]: Codec[String, T, TextPlain] = ${ derivedValueClassImpl[T] }
  private def derivedValueClassImpl[T: Type](using q: Quotes): Expr[Codec[String, T, TextPlain]] = {
    import q.reflect.*

    val tpe = TypeRepr.of[T]

    val isValueClass = tpe.baseClasses.contains(Symbol.classSymbol("scala.AnyVal"))

    if (!isValueClass) {
      report.errorAndAbort(s"Can only derive codec for value class.")
    }

    val field = tpe.typeSymbol.caseFields.head
    val fieldTpe = tpe.memberType(field)

    val baseCodec = fieldTpe.asType match
      case '[f] =>
        Expr.summon[Codec[String, f, TextPlain]].getOrElse {
          report.errorAndAbort(
            s"Cannot summon codec for value class ${tpe.show} wrapping ${fieldTpe.show}."
          )
        }

    def decodeBody(term: Term): Expr[T] = Apply(Select.unique(New(Inferred(tpe)), "<init>"), List(term)).asExprOf[T]

    def decode[F: Type]: Expr[F => T] = '{ (f: F) => ${ decodeBody('{ f }.asTerm) } }

    def encodeBody[F: Type](term: Term): Expr[F] = Select(term, field).asExprOf[F]

    def encode[F: Type]: Expr[T => F] = '{ (t: T) => ${ encodeBody[F]('{ t }.asTerm) } }

    fieldTpe.asType match
      case '[f] =>
        val dec = decode[f]
        val enc = encode[f]

        '{ $baseCodec.asInstanceOf[Codec[String, f, TextPlain]].map($dec)($enc) }
  }

}
