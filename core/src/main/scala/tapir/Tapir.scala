package tapir

import tapir.Codec.{RequiredPlainCodec, PlainCodec}

trait Tapir {
  def path[T: RequiredPlainCodec]: EndpointInput[T] =
    EndpointInput.PathCapture(implicitly[RequiredPlainCodec[T]], None, None, None)
  def path[T: RequiredPlainCodec](name: String): EndpointInput[T] =
    EndpointInput.PathCapture(implicitly[RequiredPlainCodec[T]], Some(name), None, None)
  implicit def stringToPath(s: String): EndpointInput[Unit] = EndpointInput.PathSegment(s)

  def query[T: PlainCodec](name: String): EndpointInput.Query[T] = EndpointInput.Query(name, implicitly[PlainCodec[T]], None, None)

  def body[T, M <: MediaType, R](implicit tm: Codec[T, M, R]): EndpointIO.Body[T, M, R] = EndpointIO.Body(tm, None, None)
  def stringBody: EndpointIO.Body[String, MediaType.TextPlain, String] =
    EndpointIO.Body(implicitly[Codec[String, MediaType.TextPlain, String]], None, None)
  def plainBody[T](implicit codec: Codec[T, MediaType.TextPlain, String]): EndpointIO.Body[T, MediaType.TextPlain, String] =
    EndpointIO.Body(codec, None, None)
  def jsonBody[T](implicit codec: Codec[T, MediaType.Json, String]): EndpointIO.Body[T, MediaType.Json, String] =
    EndpointIO.Body(codec, None, None)

  def byteArrayBody: EndpointIO.Body[Array[Byte], MediaType.OctetStream, Array[Byte]] =
    EndpointIO.Body(implicitly[Codec[Array[Byte], MediaType.OctetStream, Array[Byte]]], None, None)

  def header[T: PlainCodec](name: String): EndpointIO.Header[T] = EndpointIO.Header(name, implicitly[PlainCodec[T]], None, None)

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
