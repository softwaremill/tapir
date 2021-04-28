package sttp.tapir

trait EndpointInputsMacros[I, E, O, -R] { this: EndpointInputsOps[I, E, O, R] =>
  def mapInTo[CASE_CLASS]: EndpointType[CASE_CLASS, E, O, R] = ???
//  def mapInTo[COMPANION, CASE_CLASS <: Product](
//      c: COMPANION
//  )(implicit fc: FnComponents[COMPANION, I, CASE_CLASS]): EndpointType[CASE_CLASS, E, O, R] =
//    withInput[CASE_CLASS, R](input = input.mapTo(c)(fc))
}

trait EndpointErrorOutputsMacros[I, E, O, -R] { this: EndpointErrorOutputsOps[I, E, O, R] =>
  def mapErrorOutTo[CASE_CLASS]: EndpointType[I, CASE_CLASS, O, R] = ???
//  def mapErrorOutTo[COMPANION, CASE_CLASS <: Product](
//      c: COMPANION
//  )(implicit fc: FnComponents[COMPANION, E, CASE_CLASS]): EndpointType[I, CASE_CLASS, O, R] =
//    withErrorOutput(errorOutput.mapTo(c)(fc))
}

trait EndpointOutputsMacros[I, E, O, -R] { this: EndpointOutputsOps[I, E, O, R] =>
  def mapOutTo[CASE_CLASS]: EndpointType[I, E, CASE_CLASS, R] = ???
//  def mapOutTo[COMPANION, CASE_CLASS <: Product](
//      c: COMPANION
//  )(implicit fc: FnComponents[COMPANION, O, CASE_CLASS]): EndpointType[I, E, CASE_CLASS, R] =
//    withOutput(output.mapTo(c)(fc))
}
