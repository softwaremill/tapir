package tapir

import java.io.{File, InputStream}
import java.nio.ByteBuffer
import java.nio.file.Path

import tapir.GeneralCodec.{GeneralPlainCodec, PlainCodec}

trait Tapir {
  def path[T: PlainCodec]: EndpointInput[T] =
    EndpointInput.PathCapture(implicitly[PlainCodec[T]], None, None, None)
  def path[T: PlainCodec](name: String): EndpointInput[T] =
    EndpointInput.PathCapture(implicitly[PlainCodec[T]], Some(name), None, None)
  implicit def stringToPath(s: String): EndpointInput[Unit] = EndpointInput.PathSegment(s)

  def query[T: GeneralPlainCodec](name: String): EndpointInput.Query[T] =
    EndpointInput.Query(name, implicitly[GeneralPlainCodec[T]], None, None)

  def body[T, M <: MediaType, R](implicit tm: GeneralCodec[T, M, R]): EndpointIO.Body[T, M, R] = EndpointIO.Body(tm, None, None)
  def stringBody: EndpointIO.Body[String, MediaType.TextPlain, String] =
    EndpointIO.Body(implicitly[GeneralCodec[String, MediaType.TextPlain, String]], None, None)
  def plainBody[T](implicit codec: GeneralCodec[T, MediaType.TextPlain, String]): EndpointIO.Body[T, MediaType.TextPlain, String] =
    EndpointIO.Body(codec, None, None)
  def jsonBody[T](implicit codec: GeneralCodec[T, MediaType.Json, String]): EndpointIO.Body[T, MediaType.Json, String] =
    EndpointIO.Body(codec, None, None)

  def byteArrayBody: EndpointIO.Body[Array[Byte], MediaType.OctetStream, Array[Byte]] = EndpointIO.Body(implicitly, None, None)
  def byteBufferBody: EndpointIO.Body[ByteBuffer, MediaType.OctetStream, ByteBuffer] = EndpointIO.Body(implicitly, None, None)
  def inputStreamBody: EndpointIO.Body[InputStream, MediaType.OctetStream, InputStream] = EndpointIO.Body(implicitly, None, None)

  def fileBody: EndpointIO.Body[File, MediaType.OctetStream, File] = EndpointIO.Body(implicitly, None, None)
  def pathBody: EndpointIO.Body[Path, MediaType.OctetStream, File] = EndpointIO.Body(implicitly, None, None)

  def header[T: GeneralPlainCodec](name: String): EndpointIO.Header[T] =
    EndpointIO.Header(name, implicitly[GeneralPlainCodec[T]], None, None)

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
