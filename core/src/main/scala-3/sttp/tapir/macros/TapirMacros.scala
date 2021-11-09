package sttp.tapir.macros

import sttp.model.StatusCode
import sttp.tapir.EndpointOutput.OneOfVariant
import sttp.tapir.{EndpointOutput, Tapir}

import scala.reflect.ClassTag
import scala.reflect.classTag
import scala.quoted.*

trait TapirMacros { this: Tapir =>

  /** Create a one-of-variant which uses `output` if the class of the provided value (when interpreting as a server) matches the runtime
    * class of `T`.
    *
    * This will fail at compile-time if the type erasure of `T` is different from `T`, as a runtime check in this situation would give
    * invalid results. In such cases, use [[oneOfVariantClassMatcher]], [[oneOfVariantValueMatcher]] or [[oneOfVariantFromMatchType]]
    * instead.
    *
    * Should be used in [[oneOf]] output descriptions.
    */
  inline def oneOfVariant[T: ClassTag](output: EndpointOutput[T]): OneOfVariant[T] =
    ${ TapirMacros.oneOfVariantImpl[T]('output, '{ classTag[T] }) }

  /** Create a one-of-variant which uses `output` if the class of the provided value (when interpreting as a server) matches the runtime
    * class of `T`. Adds a fixed status-code output with the given value.
    *
    * This will fail at compile-time if the type erasure of `T` is different from `T`, as a runtime check in this situation would give
    * invalid results. In such cases, use [[oneOfVariantClassMatcher]], [[oneOfVariantValueMatcher]] or [[oneOfVariantFromMatchType]]
    * instead.
    *
    * Should be used in [[oneOf]] output descriptions.
    */
  inline def oneOfVariant[T: ClassTag](statusCode: StatusCode, output: EndpointOutput[T]): OneOfVariant[T] =
    ${ TapirMacros.oneOfVariantImpl[T]('statusCode, 'output, '{ classTag[T] }) }

  @deprecated("Use oneOfVariant", since = "0.19.0")
  inline def oneOfMapping[T: ClassTag](statusCode: StatusCode, output: EndpointOutput[T]): OneOfVariant[T] =
    ${ TapirMacros.oneOfVariantImpl[T]('statusCode, 'output, '{ classTag[T] }) }
}

object TapirMacros {
  def oneOfVariantImpl[T: Type](output: Expr[EndpointOutput[T]], ct: Expr[ClassTag[T]])(using
      Quotes
  ): Expr[OneOfVariant[T]] = {
    import quotes.reflect._
    mustBeEqualToItsErasure[T]
    '{ sttp.tapir.oneOfVariantClassMatcher($output, $ct.runtimeClass) }
  }

  def oneOfVariantImpl[T: Type](statusCode: Expr[StatusCode], output: Expr[EndpointOutput[T]], ct: Expr[ClassTag[T]])(using
      Quotes
  ): Expr[OneOfVariant[T]] = {
    import quotes.reflect._
    mustBeEqualToItsErasure[T]
    '{ sttp.tapir.oneOfVariantClassMatcher(sttp.tapir.statusCode($statusCode).and($output), $ct.runtimeClass) }
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
        s"Constructing oneOfVariant of type ${t.show}, $t is not allowed because of type erasure. Using a runtime-class-based check it isn't possible to verify " +
          s"that the input matches the desired class. Please use oneOfVariantClassMatcher, oneOfVariantValueMatcher or oneOfVariantFromMatchType instead"
      )
    }
  }
}
