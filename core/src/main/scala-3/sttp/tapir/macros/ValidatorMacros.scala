package sttp.tapir.macros

import sttp.tapir.Validator
import sttp.tapir.SchemaType.SObjectInfo

import scala.quoted.{Expr, Quotes, Type}
import scala.compiletime

import scala.quoted.*

trait ValidatorMacros {

  /** Creates an enum validator where all subtypes of the sealed hierarchy `T` are `object`s.
    * This enumeration will only be used for documentation, as a value outside of the allowed values will not be
    * decoded in the first place (the decoder has no other option than to fail).
    */
  inline def derivedEnumeration[T]: Validator.Enumeration[T] = ${ValidatorMacros.derivedEnumerationImpl[T]}
}

object ValidatorMacros {
  def derivedEnumerationImpl[T: Type](using q: Quotes): Expr[Validator.Enumeration[T]] = {
    import quotes.reflect.*

    val tpe = TypeRepr.of[T]
    val symbol = tpe.typeSymbol

    if (!symbol.isClassDef || !(symbol.flags is Flags.Sealed)) {
      report.throwError("Can only enumerate values of a sealed trait or class.")
    } else {
      val children = symbol.children.toList.sortBy(_.name)
      
      if (!children.forall(_.flags is Flags.Module)) {
        report.throwError("All children must be objects.")
      } else {
        val instances = children.map(x => tpe.memberType(x).asType match { 
          case '[f] => Ref(x).asExprOf[f]
        })

        '{
          Validator.Enumeration[T](
            List(${Varargs(instances)}: _*).asInstanceOf[List[T]],
            None,
            None//TODO
          )
        }
      }
    }
  }
}