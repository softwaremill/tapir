package sttp.tapir.macros

import sttp.tapir.EndpointTransput
import sttp.tapir.internal.EndpointMapMacros

import scala.compiletime.erasedValue
import scala.deriving.Mirror

trait EndpointTransputMacros[T] { this: EndpointTransput[T] =>
  inline def mapTo[CASE_CLASS <: Product](using mc: Mirror.ProductOf[CASE_CLASS]): ThisType[CASE_CLASS] = 
    this.map(EndpointMapMacros.mappingImpl[T, CASE_CLASS])

}