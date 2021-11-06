package sttp.tapir.macros

import sttp.tapir.internal.MapToMacro
import sttp.tapir.{EndpointErrorOutputsOps, EndpointInputsOps, EndpointOutputsOps, EndpointSecurityInputsOps}

trait EndpointSecurityInputsMacros[A, I, E, O, -R] { this: EndpointSecurityInputsOps[A, I, E, O, R] =>
  def mapSecurityInTo[CASE_CLASS]: EndpointType[CASE_CLASS, I, E, O, R] =
    macro MapToMacro.generateMapSecurityInTo[EndpointType[CASE_CLASS, I, E, O, R], I, CASE_CLASS]
}

trait EndpointInputsMacros[A, I, E, O, -R] { this: EndpointInputsOps[A, I, E, O, R] =>
  def mapInTo[CASE_CLASS]: EndpointType[A, CASE_CLASS, E, O, R] =
    macro MapToMacro.generateMapInTo[EndpointType[A, CASE_CLASS, E, O, R], I, CASE_CLASS]
}

trait EndpointErrorOutputsMacros[A, I, E, O, -R] { this: EndpointErrorOutputsOps[A, I, E, O, R] =>
  def mapErrorOutTo[CASE_CLASS]: EndpointType[A, I, CASE_CLASS, O, R] =
    macro MapToMacro.generateMapErrorOutTo[EndpointType[A, I, CASE_CLASS, O, R], E, CASE_CLASS]
}

trait EndpointOutputsMacros[A, I, E, O, -R] { this: EndpointOutputsOps[A, I, E, O, R] =>
  def mapOutTo[CASE_CLASS]: EndpointType[A, I, E, CASE_CLASS, R] =
    macro MapToMacro.generateMapOutTo[EndpointType[A, I, E, CASE_CLASS, R], O, CASE_CLASS]
}
