package sttp.tapir.macros

import sttp.tapir.{EndpointErrorOutputsOps, EndpointInputsOps, EndpointOutputsOps, EndpointTransput, Mapping}
import sttp.tapir.internal.EndpointMapMacros

import scala.compiletime.erasedValue
import scala.deriving.Mirror

trait EndpointInputsMacros[I, E, O, -R] { this: EndpointInputsOps[I, E, O, R] =>
  inline def mapInTo[CASE_CLASS <: Product](using mc: Mirror.ProductOf[CASE_CLASS]): EndpointType[CASE_CLASS, E, O, R] = 
    this.mapIn(EndpointMapMacros.mappingImpl[I, CASE_CLASS])
}

trait EndpointErrorOutputsMacros[I, E, O, -R] { this: EndpointErrorOutputsOps[I, E, O, R] =>
  inline def mapErrorOutTo[CASE_CLASS <: Product](using mc: Mirror.ProductOf[CASE_CLASS]): EndpointType[I, CASE_CLASS, O, R] = 
    this.mapErrorOut(EndpointMapMacros.mappingImpl[E, CASE_CLASS])
}

trait EndpointOutputsMacros[I, E, O, -R] { this: EndpointOutputsOps[I, E, O, R] =>
  inline def mapOutTo[CASE_CLASS <: Product](using mc: Mirror.ProductOf[CASE_CLASS]): EndpointType[I, E, CASE_CLASS, R] = 
    this.mapOut(EndpointMapMacros.mappingImpl[O, CASE_CLASS])
}
