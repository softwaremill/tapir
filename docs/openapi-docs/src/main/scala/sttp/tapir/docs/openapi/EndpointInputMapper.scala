package sttp.tapir.docs.openapi

import sttp.tapir.{EndpointIO, EndpointInput}

// ideally the parameters would be polymporphic functions returning EI[I] => EI[I]
private[openapi] class EndpointInputMapper[S](
    inputMapping: PartialFunction[(EndpointInput.Single[_], S), (EndpointInput.Single[_], S)],
    ioMapping: PartialFunction[(EndpointIO.Single[_], S), (EndpointIO.Single[_], S)]
) {
  def mapInput(ei: EndpointInput[_], s: S): (EndpointInput[_], S) = ei match {
    case single: EndpointInput.Single[_] => mapInputSingle(single, s)
    case eio: EndpointIO[_]              => mapIO(eio, s)
    case EndpointInput.Tuple(inputs) =>
      val (inputs2, s2) = inputs.foldLeft((Vector.empty[EndpointInput.Single[_]], s)) {
        case ((rebuilt, s3), nested) =>
          val (nested2, s4) = mapInputSingle(nested, s3)
          (rebuilt :+ nested2, s4)
      }
      (EndpointInput.Tuple(inputs2), s2)
  }

  private def mapInputSingle(ei: EndpointInput.Single[_], s: S): (EndpointInput.Single[_], S) = ei match {
    case _ if inputMapping.isDefinedAt((ei, s)) => inputMapping((ei, s))
    case EndpointInput.MappedTuple(wrapped, c) =>
      val (wrapped2, s2) = mapInput(wrapped, s)
      (EndpointInput.MappedTuple(wrapped2.asInstanceOf[EndpointInput.Tuple[Any]], c), s2)
    case _ => (ei, s)
  }

  private def mapIO(ei: EndpointIO[_], s: S): (EndpointIO[_], S) = ei match {
    case single: EndpointIO.Single[_] => mapIOSingle(single, s)
    case EndpointIO.Tuple(inputs) =>
      val (inputs2, s2) = inputs.foldLeft((Vector.empty[EndpointIO.Single[_]], s)) {
        case ((rebuilt, s3), nested) =>
          val (nested2, s4) = mapIOSingle(nested, s3)
          (rebuilt :+ nested2, s4)
      }
      (EndpointIO.Tuple(inputs2), s2)
  }

  private def mapIOSingle(ei: EndpointIO.Single[_], s: S): (EndpointIO.Single[_], S) = ei match {
    case _ if ioMapping.isDefinedAt((ei, s)) => ioMapping((ei, s))
    case EndpointIO.MappedTuple(wrapped, c) =>
      val (wrapped2, s2) = mapIO(wrapped, s)
      (EndpointIO.MappedTuple(wrapped2.asInstanceOf[EndpointIO.Tuple[Any]], c), s2)
    case _ => (ei, s)
  }
}
