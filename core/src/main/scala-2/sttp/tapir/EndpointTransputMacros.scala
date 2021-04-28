package sttp.tapir

trait EndpointTransputMacros[T] { this: EndpointTransput[T] =>
  def mapTo[CASE_CLASS]: ThisType[CASE_CLASS] = ???

  /*
    def mapTo[U]: ThisType[U] = {
    map[CASE_CLASS](fc.tupled(c).apply(_))(ProductToParams(_, fc.arity).asInstanceOf[T])
  }
   */
}
