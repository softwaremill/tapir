package sttp.tapir.macros

import sttp.tapir.{EndpointErrorOutputsOps, EndpointSecurityInputsOps, EndpointInputsOps, EndpointOutputsOps}
import sttp.tapir.internal.MappingMacros

import scala.deriving.Mirror

trait EndpointSecurityInputsMacros[A, I, E, O, -R] { this: EndpointSecurityInputsOps[A, I, E, O, R] =>
  inline def mapSecurityInTo[CASE_CLASS <: Product](using mc: Mirror.ProductOf[CASE_CLASS]): EndpointType[CASE_CLASS, I, E, O, R] =
    this.mapSecurityIn(MappingMacros.mappingImpl[A, CASE_CLASS])
}

trait EndpointInputsMacros[A, I, E, O, -R] { this: EndpointInputsOps[A, I, E, O, R] =>
  inline def mapInTo[CASE_CLASS <: Product](using mc: Mirror.ProductOf[CASE_CLASS]): EndpointType[A, CASE_CLASS, E, O, R] =
    this.mapIn(MappingMacros.mappingImpl[I, CASE_CLASS])
}

trait EndpointErrorOutputsMacros[A, I, E, O, -R] { this: EndpointErrorOutputsOps[A, I, E, O, R] =>
  inline def mapErrorOutTo[CASE_CLASS <: Product](using mc: Mirror.ProductOf[CASE_CLASS]): EndpointType[A, I, CASE_CLASS, O, R] =
    this.mapErrorOut(MappingMacros.mappingImpl[E, CASE_CLASS])
}

trait EndpointOutputsMacros[A, I, E, O, -R] { this: EndpointOutputsOps[A, I, E, O, R] =>
  inline def mapOutTo[CASE_CLASS <: Product](using mc: Mirror.ProductOf[CASE_CLASS]): EndpointType[A, I, E, CASE_CLASS, R] =
    this.mapOut(MappingMacros.mappingImpl[O, CASE_CLASS])
}
