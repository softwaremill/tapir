package sttp.tapir.macros

import sttp.tapir.EndpointTransput
import sttp.tapir.internal.MapToMacro

trait EndpointTransputMacros[T] { this: EndpointTransput[T] =>
  def mapTo[CASE_CLASS]: ThisType[CASE_CLASS] = macro MapToMacro.generateMapTo[ThisType, T, CASE_CLASS]
}
