package sttp.tapir.macros

import sttp.model.StatusCode
import sttp.tapir.EndpointOutput.OneOfMapping
import sttp.tapir.EndpointOutput

import scala.reflect.ClassTag
import scala.reflect.classTag

import scala.quoted.*

trait TapirMacros {

  /** Create a one-of-mapping which uses `output` if the class of the provided value (when interpreting as a server) matches the runtime
    * class of `T`.
    *
    * This will fail at compile-time if the type erasure of `T` is different from `T`, as a runtime check in this situation would give
    * invalid results. In such cases, use [[oneOfMappingClassMatcher]], [[oneOfMappingValueMatcher]] or [[oneOfMappingFromMatchType]]
    * instead.
    *
    * Should be used in [[oneOf]] output descriptions.
    */
  inline def oneOfMapping[T: ClassTag](output: EndpointOutput[T]): OneOfMapping[T] =
    ${ TapirMacros.oneOfMappingImpl[T]('output, '{ classTag[T] }) }

  /** Create a one-of-mapping which uses `output` if the class of the provided value (when interpreting as a server) matches the runtime
    * class of `T`. Adds a fixed status-code output with the given value.
    *
    * This will fail at compile-time if the type erasure of `T` is different from `T`, as a runtime check in this situation would give
    * invalid results. In such cases, use [[oneOfMappingClassMatcher]], [[oneOfMappingValueMatcher]] or [[oneOfMappingFromMatchType]]
    * instead.
    *
    * Should be used in [[oneOf]] output descriptions.
    */
  inline def oneOfMapping[T: ClassTag](statusCode: StatusCode, output: EndpointOutput[T]): OneOfMapping[T] =
    ${ TapirMacros.oneOfMappingImpl[T]('statusCode, 'output, '{ classTag[T] }) }
}

object TapirMacros {
  def oneOfMappingImpl[T: Type](output: Expr[EndpointOutput[T]], ct: Expr[ClassTag[T]])(using
      Quotes
  ): Expr[OneOfMapping[T]] = {
    import quotes.reflect._
    mustBeEqualToItsErasure[T]
    '{ sttp.tapir.oneOfMappingClassMatcher($output, $ct.runtimeClass) }
  }

  def oneOfMappingImpl[T: Type](statusCode: Expr[StatusCode], output: Expr[EndpointOutput[T]], ct: Expr[ClassTag[T]])(using
      Quotes
  ): Expr[OneOfMapping[T]] = {
    import quotes.reflect._
    mustBeEqualToItsErasure[T]
    '{ sttp.tapir.oneOfMappingClassMatcher(sttp.tapir.statusCode($statusCode).and($output), $ct.runtimeClass) }
  }

  private def mustBeEqualToItsErasure[T: Type](using Quotes): Unit = {
    import quotes.reflect._

    val t = TypeRepr.of[T]

    // AppliedType(TypeRef(TermRef(ThisType(TypeRef(NoPrefix,module class <root>)),object scala),Array),List(TypeRef(TermRef(ThisType(TypeRef(NoPrefix,module class <root>)),object scala),Byte)))

    // substitute for `t =:= t.erasure` - https://github.com/lampepfl/dotty-feature-requests/issues/209
    val isAllowed: TypeRepr => Boolean = {
      case AppliedType(t, _) if t.typeSymbol.name == "Array" => true
      case _: AppliedType | _: AndOrType                     => false
      case _                                                 => true
    }

    if (!isAllowed(t)) {
      report.throwError(
        s"Constructing oneOfMapping of type ${t.show}, $t is not allowed because of type erasure. Using a runtime-class-based check it isn't possible to verify " +
          s"that the input matches the desired class. Please use oneOfMappingClassMatcher, oneOfMappingValueMatcher or oneOfMappingFromMatchType instead"
      )
    }
  }
}
