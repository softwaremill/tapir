package sttp.tapir.macros

import sttp.tapir.EndpointInput
import sttp.tapir.internal.AnnotationsMacros

import scala.quoted.*

trait EndpointInputMacros {

  /** Derives an input description using metadata specified with annotations on the given case class. Each field of the case class must be
    * annotated with one of the annotations from [[sttp.tapir.EndpointIO.annotations]]. Additional schema meta-data can be specified using
    * annotations from [[sttp.tapir.Schema.annotations]]. The result is mapped to an instance of the [[T]] type.
    */
  inline def derived[T <: Product]: EndpointInput[T] = ${ EndpointInputMacros.derivedImpl[T] }
}

private[tapir] object EndpointInputMacros {
  def derivedImpl[T <: Product: Type](using q: Quotes): Expr[EndpointInput[T]] = new AnnotationsMacros[T].deriveEndpointInputImpl
}
