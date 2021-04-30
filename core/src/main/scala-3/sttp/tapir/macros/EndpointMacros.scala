package sttp.tapir.macros

import sttp.tapir.{EndpointErrorOutputsOps, EndpointInputsOps, EndpointOutputsOps}

trait EndpointInputsMacros[I, E, O, -R] { this: EndpointInputsOps[I, E, O, R] =>
  def mapInTo[CASE_CLASS]: EndpointType[CASE_CLASS, E, O, R] = ??? // TODO
}

trait EndpointErrorOutputsMacros[I, E, O, -R] { this: EndpointErrorOutputsOps[I, E, O, R] =>
  def mapErrorOutTo[CASE_CLASS]: EndpointType[I, CASE_CLASS, O, R] = ??? // TODO
}

trait EndpointOutputsMacros[I, E, O, -R] { this: EndpointOutputsOps[I, E, O, R] =>
  def mapOutTo[CASE_CLASS]: EndpointType[I, E, CASE_CLASS, R] = ??? // TODO
}
