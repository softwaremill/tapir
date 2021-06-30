package sttp.tapir.macros

import sttp.tapir.EndpointInput
import sttp.tapir.internal.AnnotationsMacros

import scala.quoted.*
import scala.deriving.Mirror

trait EndpointInputMacros {
  inline def derived[T <: Product]: EndpointInput[T] = ${EndpointInputMacros.derivedImpl[T]}
}

object EndpointInputMacros {
  def derivedImpl[T <: Product: Type](using q: Quotes): Expr[EndpointInput[T]] = new AnnotationsMacros[T].deriveEndpointInputImpl
}
