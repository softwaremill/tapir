package sttp.tapir.macros

import sttp.tapir.EndpointTransput

trait EndpointTransputMacros[T] { this: EndpointTransput[T] =>
  def mapTo[CASE_CLASS]: ThisType[CASE_CLASS] = ??? // TODO
}
