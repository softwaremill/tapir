package sttp.tapir.server.ziohttp

import sttp.capabilities
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interpreter.{BodyMaxLengthExceededException, RawValue, RequestBody}
import sttp.tapir.{FileRange, InputStreamRange, RawBodyType}
import zio.http.Request
import zio.stream.Stream
import zio.{RIO, Task, ZIO}

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import sttp.capabilities.StreamMaxLengthExceededException

class ZioHttpRequestBody[R](serverOptions: ZioHttpServerOptions[R]) extends RequestBody[RIO[R, *], ZioStreams] {
  override val streams: capabilities.Streams[ZioStreams] = ZioStreams

  override def toRaw[RAW](serverRequest: ServerRequest, bodyType: RawBodyType[RAW], maxBytes: Option[Long]): Task[RawValue[RAW]] = bodyType match {
    case RawBodyType.StringBody(defaultCharset) => asByteArray(serverRequest).map(new String(_, defaultCharset)).map(RawValue(_))
    case RawBodyType.ByteArrayBody              => asByteArray(serverRequest).map(RawValue(_))
    case RawBodyType.ByteBufferBody             => asByteArray(serverRequest).map(bytes => ByteBuffer.wrap(bytes)).map(RawValue(_))
    case RawBodyType.InputStreamBody            => asByteArray(serverRequest).map(new ByteArrayInputStream(_)).map(RawValue(_))
    case RawBodyType.InputStreamRangeBody =>
      asByteArray(serverRequest).map(bytes => new InputStreamRange(() => new ByteArrayInputStream(bytes))).map(RawValue(_))
    case RawBodyType.FileBody =>
      serverOptions.createFile(serverRequest).map(d => FileRange(d)).flatMap(file => ZIO.succeed(RawValue(file, Seq(file))))
    case RawBodyType.MultipartBody(_, _) => ZIO.fail(new UnsupportedOperationException("Multipart is not supported"))
  }

  override def toStream(serverRequest: ServerRequest, maxBytes: Option[Long]): streams.BinaryStream =
    toZioStream(serverRequest, maxBytes).asInstanceOf[streams.BinaryStream]

  private def toZioStream(serverRequest: ServerRequest, maxBytes: Option[Long]): Stream[Throwable, Byte] = {
    val inputStream = stream(serverRequest)
    maxBytes.map(ZioStreams.limitBytes(inputStream, _)).getOrElse(inputStream)
  }

  private def stream(serverRequest: ServerRequest): Stream[Throwable, Byte] =
    zioHttpRequest(serverRequest).body.asStream

  private def asByteArray(serverRequest: ServerRequest): Task[Array[Byte]] = {
    val body = zioHttpRequest(serverRequest).body
    if (body.isComplete) {
      maxBytes.map(limit => body.asArray.filterOrFail(_.length <= limit)(new BodyMaxLengthExceededException(limit))).getOrElse(body.asArray)
    } else
      toZioStream(serverRequest, maxBytes).runCollect
        .catchSomeDefect { case e: StreamMaxLengthExceededException =>
          ZIO.fail(e)
        }
        .map(_.toArray)
  }

  private def zioHttpRequest(serverRequest: ServerRequest) = serverRequest.underlying.asInstanceOf[Request]
}
