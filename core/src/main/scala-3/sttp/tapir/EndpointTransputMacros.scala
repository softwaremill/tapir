package sttp.tapir

trait EndpointTransputMacros[T] { this: EndpointTransput[T] =>
  def mapTo[CASE_CLASS]: ThisType[CASE_CLASS] = ??? // TODO
}
