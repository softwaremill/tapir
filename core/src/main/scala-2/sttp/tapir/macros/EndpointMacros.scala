package sttp.tapir.macros

import sttp.tapir.internal.MapToMacro
import sttp.tapir.{EndpointErrorOutputsOps, EndpointInputsOps, EndpointOutputsOps}

trait EndpointInputsMacros[I, E, O, -R] { this: EndpointInputsOps[I, E, O, R] =>
  def mapInTo[CASE_CLASS]: EndpointType[CASE_CLASS, E, O, R] =
    macro MapToMacro.generateMapInTo[EndpointType[CASE_CLASS, E, O, R], I, CASE_CLASS]
}

trait EndpointErrorOutputsMacros[I, E, O, -R] { this: EndpointErrorOutputsOps[I, E, O, R] =>
  def mapErrorOutTo[CASE_CLASS]: EndpointType[I, CASE_CLASS, O, R] =
    macro MapToMacro.generateMapErrorOutTo[EndpointType[I, CASE_CLASS, O, R], E, CASE_CLASS]
}

trait EndpointOutputsMacros[I, E, O, -R] { this: EndpointOutputsOps[I, E, O, R] =>
  def mapOutTo[CASE_CLASS]: EndpointType[I, E, CASE_CLASS, R] =
    macro MapToMacro.generateMapOutTo[EndpointType[I, E, CASE_CLASS, R], O, CASE_CLASS]
}
