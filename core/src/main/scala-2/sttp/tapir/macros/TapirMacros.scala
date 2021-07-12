package sttp.tapir.macros

import sttp.model.StatusCode
import sttp.tapir.EndpointOutput
import sttp.tapir.EndpointOutput.OneOfMapping
import sttp.tapir.internal.OneOfMappingMacro

import scala.reflect.ClassTag

trait TapirMacros {

  /** Create a one-of-mapping which uses `statusCode` and `output` if the class of the provided value (when interpreting
    * as a server) matches the runtime class of `T`.
    *
    * This will fail at compile-time if the type erasure of `T` is different from `T`, as a runtime check in this
    * situation would give invalid results. In such cases, use [[oneOfMappingClassMatcher]],
    * [[oneOfMappingValueMatcher]] or [[oneOfMappingFromMatchType]] instead.
    *
    * Should be used in [[oneOf]] output descriptions.
    */
  def oneOfMapping[T: ClassTag](statusCode: StatusCode, output: EndpointOutput[T]): OneOfMapping[T] =
    macro OneOfMappingMacro.generateClassMatcherIfErasedSameAsType[T]

  @scala.deprecated("Use oneOfMapping", since = "0.18")
  def statusMapping[T: ClassTag](statusCode: StatusCode, output: EndpointOutput[T]): OneOfMapping[T] =
    macro OneOfMappingMacro.generateClassMatcherIfErasedSameAsType[T]
}
