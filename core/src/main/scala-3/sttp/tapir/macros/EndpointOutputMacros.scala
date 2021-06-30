package sttp.tapir.macros

import sttp.tapir.EndpointOutput
import sttp.tapir.internal.AnnotationsMacros

import scala.quoted.*
import scala.deriving.Mirror

trait EndpointOutputMacros {
  inline def derived[T <: Product]: EndpointOutput[T] = ${EndpointOutputMacros.derivedImpl[T]}
}

object EndpointOutputMacros {
  def derivedImpl[T <: Product: Type](using q: Quotes): Expr[EndpointOutput[T]] = new AnnotationsMacros[T].deriveEndpointOutputImpl
}