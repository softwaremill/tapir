package sttp.tapir.macros

import sttp.tapir.internal.EnumerationMacros.*
import sttp.tapir.Validator
import sttp.tapir.Schema

import scala.quoted.*

trait ValidatorMacros {

  /** Creates an enum validator for an `enum` where all cases are parameterless, or where all subtypes of the sealed hierarchy `T` are
    * `object`s. This enumeration will only be used for documentation, as a value outside of the allowed values will not be decoded in the
    * first place (the decoder has no other option than to fail).
    */
  inline def derivedEnumeration[T]: Validator.Enumeration[T] = ${ ValidatorMacros.derivedEnumerationImpl[T] }

  inline def derivedStringBasedUnionEnumeration[T](using IsUnionOf[String, T]): Validator.Enumeration[T] = {
    lazy val values = UnionDerivation.constValueUnionTuple[String, T]
    Validator.enumeration(values.toList.asInstanceOf[List[T]])
  }
}

private[tapir] object ValidatorMacros {
  def derivedEnumerationImpl[T: Type](using q: Quotes): Expr[Validator.Enumeration[T]] = {
    import quotes.reflect.*

    val tpe = TypeRepr.of[T]
    val symbol = tpe.typeSymbol

    if (!symbol.isClassDef || !(symbol.flags is Flags.Sealed)) {
      report.errorAndAbort("Can only enumerate values of a sealed trait, class or enum.")
    }

    val instances = enumerationTypeChildren[T](failOnError = true)
      .flatMap(_.toList)
      .distinct
      .sortBy(_.name)
      .map(x =>
        tpe.memberType(x).asType match {
          case '[f] => Ref(x).asExprOf[f]
        }
      )
    val name = '{ Option(Schema.SName(${ Expr(symbol.fullName) })) }

    '{
      Validator.Enumeration[T](
        List(${ Varargs(instances) }: _*).asInstanceOf[List[T]],
        None,
        ${ name }
      )
    }
  }
}
