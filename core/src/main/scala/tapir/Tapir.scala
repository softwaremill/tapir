package tapir

import tapir.Codec.{RequiredTextCodec, TextCodec}

trait Tapir {
  def path[T: RequiredTextCodec]: EndpointInput[T] =
    EndpointInput.PathCapture(implicitly[RequiredTextCodec[T]], None, None, None)
  def path[T: RequiredTextCodec](name: String): EndpointInput[T] =
    EndpointInput.PathCapture(implicitly[RequiredTextCodec[T]], Some(name), None, None)
  implicit def stringToPath(s: String): EndpointInput[Unit] = EndpointInput.PathSegment(s)

  def query[T: TextCodec](name: String): EndpointInput.Query[T] = EndpointInput.Query(name, implicitly[TextCodec[T]], None, None)

  def body[T, M <: MediaType](implicit tm: Codec[T, M]): EndpointIO.Body[T, M] = EndpointIO.Body(tm, None, None)
  def stringBody: EndpointIO.Body[String, MediaType.Text] = EndpointIO.Body(implicitly[Codec[String, MediaType.Text]], None, None)
  def textBody[T](implicit tm: Codec[T, MediaType.Text]): EndpointIO.Body[T, MediaType.Text] =
    EndpointIO.Body(tm, None, None)
  def jsonBody[T](implicit tm: Codec[T, MediaType.Json]): EndpointIO.Body[T, MediaType.Json] = EndpointIO.Body(tm, None, None)

  def header[T: TextCodec](name: String): EndpointIO.Header[T] = EndpointIO.Header(name, implicitly[TextCodec[T]], None, None)

  case class InvalidOutput(reason: DecodeResult[Nothing], cause: Option[Throwable]) extends Exception(cause.orNull) // TODO

  val endpoint: Endpoint[Unit, Unit, Unit] =
    Endpoint[Unit, Unit, Unit](
      Method.GET,
      EndpointInput.Multiple(Vector.empty),
      EndpointIO.Multiple(Vector.empty),
      EndpointIO.Multiple(Vector.empty),
      EndpointInfo(None, None, None, Vector.empty)
    )
}
