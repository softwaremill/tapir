package tapir

import java.nio.charset.{Charset, StandardCharsets}

import tapir.GeneralCodec.{GeneralPlainCodec, PlainCodec}

trait Tapir {
  def path[T: PlainCodec]: EndpointInput[T] =
    EndpointInput.PathCapture(implicitly[PlainCodec[T]], None, None, None)
  def path[T: PlainCodec](name: String): EndpointInput[T] =
    EndpointInput.PathCapture(implicitly[PlainCodec[T]], Some(name), None, None)
  implicit def stringToPath(s: String): EndpointInput[Unit] = EndpointInput.PathSegment(s)

  def query[T: GeneralPlainCodec](name: String): EndpointInput.Query[T] =
    EndpointInput.Query(name, implicitly[GeneralPlainCodec[T]], None, None)

  def header[T: GeneralPlainCodec](name: String): EndpointIO.Header[T] =
    EndpointIO.Header(name, implicitly[GeneralPlainCodec[T]], None, None)

  def body[T, M <: MediaType](implicit tm: GeneralCodec[T, M, _]): EndpointIO.Body[T, M, _] = EndpointIO.Body(tm, None, None)

  def stringBody: EndpointIO.Body[String, MediaType.TextPlain, String] = stringBody(StandardCharsets.UTF_8)
  def stringBody(charset: String): EndpointIO.Body[String, MediaType.TextPlain, String] = stringBody(Charset.forName(charset))
  def stringBody(charset: Charset): EndpointIO.Body[String, MediaType.TextPlain, String] =
    EndpointIO.Body(GeneralCodec.stringCodec(charset), None, None)

  def plainBody[T](implicit codec: GeneralCodec[T, MediaType.TextPlain, _]): EndpointIO.Body[T, MediaType.TextPlain, _] =
    EndpointIO.Body(codec, None, None)
  def jsonBody[T](implicit codec: GeneralCodec[T, MediaType.Json, _]): EndpointIO.Body[T, MediaType.Json, _] =
    EndpointIO.Body(codec, None, None)
  def binaryBody[T](implicit codec: GeneralCodec[T, MediaType.OctetStream, _]): EndpointIO.Body[T, MediaType.OctetStream, _] =
    EndpointIO.Body(codec, None, None)

  def formBody[T](implicit codec: GeneralCodec[T, MediaType.XWwwFormUrlencoded, _]): EndpointIO.Body[T, MediaType.XWwwFormUrlencoded, _] =
    EndpointIO.Body(codec, None, None)

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
