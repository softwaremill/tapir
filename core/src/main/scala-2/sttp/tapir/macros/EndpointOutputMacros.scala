package sttp.tapir.macros

import sttp.tapir.EndpointOutput
import sttp.tapir.internal.EndpointOutputAnnotationsMacro

trait EndpointOutputMacros {

  /** Derives an output description using metadata specified with annotations on the given case class. Each field of the case class must be
    * annotated with one of the annotations from [[sttp.tapir.EndpointIO.annotations]]. Additional schema meta-data can be specified using
    * annotations from [[sttp.tapir.Schema.annotations]]. The result is mapped to an instance of the [[T]] type.
    */
  def derived[T]: EndpointOutput[T] = macro EndpointOutputAnnotationsMacro.generateEndpointOutput[T]
}
