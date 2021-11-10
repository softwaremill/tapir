package sttp.tapir.macros

import sttp.model.StatusCode
import sttp.tapir.{EndpointOutput, Tapir}
import sttp.tapir.EndpointOutput.OneOfVariant
import sttp.tapir.internal.OneOfVariantMacro

import scala.reflect.ClassTag

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
  def oneOfVariant[T: ClassTag](output: EndpointOutput[T]): OneOfVariant[T] =
    macro OneOfVariantMacro.generateClassMatcherIfErasedSameAsType[T]

  /** Create a one-of-variant which uses `output` if the class of the provided value (when interpreting as a server) matches the runtime
    * class of `T`. Adds a fixed status-code output with the given value.
    *
    * This will fail at compile-time if the type erasure of `T` is different from `T`, as a runtime check in this situation would give
    * invalid results. In such cases, use [[oneOfVariantClassMatcher]], [[oneOfVariantValueMatcher]] or [[oneOfVariantFromMatchType]]
    * instead.
    *
    * Should be used in [[oneOf]] output descriptions.
    */
  def oneOfVariant[T: ClassTag](code: StatusCode, output: EndpointOutput[T]): OneOfVariant[T] =
    macro OneOfVariantMacro.generateClassMatcherIfErasedSameAsTypeWithStatusCode[T]

  @deprecated("Use oneOfVariant", since = "0.19.0")
  def oneOfMapping[T: ClassTag](code: StatusCode, output: EndpointOutput[T]): OneOfVariant[T] =
    macro OneOfVariantMacro.generateClassMatcherIfErasedSameAsTypeWithStatusCode[T]
}
