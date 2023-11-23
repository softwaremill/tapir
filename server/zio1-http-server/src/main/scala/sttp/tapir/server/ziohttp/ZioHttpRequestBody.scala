package sttp.tapir.server.ziohttp

import sttp.capabilities
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.{FileRange, InputStreamRange}
import sttp.tapir.RawBodyType
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interpreter.RawValue
import sttp.tapir.server.interpreter.RequestBody
import zhttp.http.Request
import zio.{RIO, Task, ZIO}
import zio.blocking.Blocking
import zio.stream.{Stream, ZSink, ZStream}

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer

class ZioHttpRequestBody[R](serverOptions: ZioHttpServerOptions[R]) extends RequestBody[RIO[R, *], ZioStreams] {
  override val streams: capabilities.Streams[ZioStreams] = ZioStreams

  override def toRaw[RAW](serverRequest: ServerRequest, bodyType: RawBodyType[RAW], maxBytes: Option[Long]): Task[RawValue[RAW]] =
    bodyType match {
      case RawBodyType.StringBody(defaultCharset) => asByteArray(serverRequest).map(new String(_, defaultCharset)).map(RawValue(_))
      case RawBodyType.ByteArrayBody              => asByteArray(serverRequest).map(RawValue(_))
      case RawBodyType.ByteBufferBody             => asByteArray(serverRequest).map(bytes => ByteBuffer.wrap(bytes)).map(RawValue(_))
      case RawBodyType.InputStreamBody            => asByteArray(serverRequest).map(new ByteArrayInputStream(_)).map(RawValue(_))
      case RawBodyType.InputStreamRangeBody =>
        asByteArray(serverRequest).map(bytes => new InputStreamRange(() => new ByteArrayInputStream(bytes))).map(RawValue(_))
      case RawBodyType.FileBody =>
        for {
          tmpFile <- serverOptions.createFile(serverRequest)
          _ <- toStream(serverRequest, maxBytes)
            .asInstanceOf[Stream[Throwable, Byte]]
            .run(ZSink.fromFile(tmpFile.toPath))
            .provideLayer(Blocking.live)
        } yield {
          val fileRange = FileRange(tmpFile)
          RawValue(fileRange, Seq(fileRange))
        }
      case RawBodyType.MultipartBody(_, _) => ZIO.fail(new UnsupportedOperationException("Multipart is not supported"))
    }

  override def toStream(serverRequest: ServerRequest, maxBytes: Option[Long]): streams.BinaryStream =
    stream(serverRequest).asInstanceOf[streams.BinaryStream]

  private def asByteArray(serverRequest: ServerRequest): Task[Array[Byte]] = zioHttpRequest(serverRequest).body.map(_.toArray)

  private def stream(serverRequest: ServerRequest): Stream[Throwable, Byte] =
    ZStream.fromEffect(zioHttpRequest(serverRequest).body).flattenChunks

  private def zioHttpRequest(serverRequest: ServerRequest) = serverRequest.underlying.asInstanceOf[Request]
}
