package sttp.tapir.server.ziohttp

import sttp.capabilities
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interpreter.{RawValue, RequestBody}
import sttp.tapir.{FileRange, InputStreamRange, RawBodyType}
import zio.http.Request
import zio.stream.{Stream, ZSink, ZStream}
import zio.{RIO, Task, ZIO}

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer

class ZioHttpRequestBody[R](serverOptions: ZioHttpServerOptions[R]) extends RequestBody[RIO[R, *], ZioStreams] {
  override val streams: capabilities.Streams[ZioStreams] = ZioStreams

  override def toRaw[RAW](serverRequest: ServerRequest, bodyType: RawBodyType[RAW], maxBytes: Option[Long]): Task[RawValue[RAW]] = {

    def asByteArray: Task[Array[Byte]] =
      (toStream(serverRequest, maxBytes).asInstanceOf[ZStream[Any, Throwable, Byte]]).runCollect.map(_.toArray)

    bodyType match {
      case RawBodyType.StringBody(defaultCharset) => asByteArray.map(new String(_, defaultCharset)).map(RawValue(_))
      case RawBodyType.ByteArrayBody              => asByteArray.map(RawValue(_))
      case RawBodyType.ByteBufferBody             => asByteArray.map(bytes => ByteBuffer.wrap(bytes)).map(RawValue(_))
      case RawBodyType.InputStreamBody            => asByteArray.map(new ByteArrayInputStream(_)).map(RawValue(_))
      case RawBodyType.InputStreamRangeBody =>
        asByteArray.map(bytes => new InputStreamRange(() => new ByteArrayInputStream(bytes))).map(RawValue(_))
      case RawBodyType.FileBody =>
        for {
          file <- serverOptions.createFile(serverRequest)
          _ <- (toStream(serverRequest, maxBytes).asInstanceOf[ZStream[Any, Throwable, Byte]]).run(ZSink.fromFile(file)).map(_ => ())
        } yield RawValue(FileRange(file), Seq(FileRange(file)))
      case RawBodyType.MultipartBody(_, _) => ZIO.fail(new UnsupportedOperationException("Multipart is not supported"))
    }
  }

  override def toStream(serverRequest: ServerRequest, maxBytes: Option[Long]): streams.BinaryStream = {
    val inputStream = stream(serverRequest)
    maxBytes.map(ZioStreams.limitBytes(inputStream, _)).getOrElse(inputStream).asInstanceOf[streams.BinaryStream]
  }

  private def stream(serverRequest: ServerRequest): Stream[Throwable, Byte] =
    zioHttpRequest(serverRequest).body.asStream

  private def zioHttpRequest(serverRequest: ServerRequest) = serverRequest.underlying.asInstanceOf[Request]
}
