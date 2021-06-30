package sttp.tapir.macros

import sttp.tapir.EndpointInput
import sttp.tapir.generic.internal.EndpointInputAnnotationsMacro

trait EndpointInputMacros {

  /** Derives an input description using metadata specified with annotations on the given case class. Each field
    * of the case class must be annotated with one of the annotations from [[sttp.tapir.EndpointIO.annotations]].
    * The result is mapped to an instance of the [[T]] type.
    */
  def derived[T]: EndpointInput[T] = macro EndpointInputAnnotationsMacro.generateEndpointInput[T]
}
