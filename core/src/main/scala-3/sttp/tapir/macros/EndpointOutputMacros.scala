package sttp.tapir.macros

import sttp.tapir.EndpointOutput
import sttp.tapir.internal.AnnotationsMacros

import scala.quoted.*

trait EndpointOutputMacros {

  /** Derives an output description using metadata specified with annotations on the given case class. Each field of the case class must be
    * annotated with one of the annotations from [[sttp.tapir.EndpointIO.annotations]]. Additional schema meta-data can be specified using
    * annotations from [[sttp.tapir.Schema.annotations]]. The result is mapped to an instance of the [[T]] type.
    */
  inline def derived[T <: Product]: EndpointOutput[T] = ${ EndpointOutputMacros.derivedImpl[T] }
}

private[tapir] object EndpointOutputMacros {
  def derivedImpl[T <: Product: Type](using q: Quotes): Expr[EndpointOutput[T]] = new AnnotationsMacros[T].deriveEndpointOutputImpl
}
