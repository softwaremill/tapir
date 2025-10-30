package sttp.tapir.docs.apispec

import sttp.tapir.{EndpointIO, EndpointInput, Mapping}

// ideally the parameters would be polymporphic functions returning EI[I] => EI[I]
private[docs] class EndpointInputMapper[S](
    inputMapping: PartialFunction[(EndpointInput.Single[_], S), (EndpointInput.Single[_], S)],
    ioMapping: PartialFunction[(EndpointIO.Single[_], S), (EndpointIO.Single[_], S)]
) {
  def mapInput(ei: EndpointInput[_], s: S): (EndpointInput[_], S) =
    ei match {
      case single: EndpointInput.Single[_]                 => mapInputSingle(single, s)
      case eio: EndpointIO[_]                              => mapIO(eio, s)
      case EndpointInput.Pair(left, right, combine, split) =>
        val (left2, s2) = mapInput(left, s)
        val (right2, s3) = mapInput(right, s2)

        (EndpointInput.Pair(left2, right2, combine, split), s3)
    }

  private def mapInputSingle(ei: EndpointInput.Single[_], s: S): (EndpointInput.Single[_], S) =
    ei match {
      case _ if inputMapping.isDefinedAt((ei, s)) => inputMapping((ei, s))
      case EndpointInput.MappedPair(wrapped, c)   =>
        val (wrapped2, s2) = mapInput(wrapped, s)
        (EndpointInput.MappedPair(wrapped2.asInstanceOf[EndpointInput.Pair[Any, Any, Any]], c.asInstanceOf[Mapping[Any, Any]]), s2)
      case _ => (ei, s)
    }

  private def mapIO(ei: EndpointIO[_], s: S): (EndpointIO[_], S) =
    ei match {
      case single: EndpointIO.Single[_]                 => mapIOSingle(single, s)
      case EndpointIO.Pair(left, right, combine, split) =>
        val (left2, s2) = mapIO(left, s)
        val (right2, s3) = mapIO(right, s2)

        (EndpointIO.Pair(left2, right2, combine, split), s3)
    }

  private def mapIOSingle(ei: EndpointIO.Single[_], s: S): (EndpointIO.Single[_], S) =
    ei match {
      case _ if ioMapping.isDefinedAt((ei, s)) => ioMapping((ei, s))
      case EndpointIO.MappedPair(wrapped, c)   =>
        val (wrapped2, s2) = mapIO(wrapped, s)
        (EndpointIO.MappedPair(wrapped2.asInstanceOf[EndpointIO.Pair[Any, Any, Any]], c.asInstanceOf[Mapping[Any, Any]]), s2)
      case _ => (ei, s)
    }
}
