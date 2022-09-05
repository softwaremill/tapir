package sttp.tapir.server.ziohttp

import sttp.capabilities
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.FileRange
import sttp.tapir.RawBodyType
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interpreter.RawValue
import sttp.tapir.server.interpreter.RequestBody
import zhttp.http.Request
import zio.{Chunk, RIO, Task, ZIO}
import zio.stream.Stream
import zio.stream.ZStream

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import scala.annotation.unused

class ZioHttpRequestBody[R](serverOptions: ZioHttpServerOptions[R]) extends RequestBody[RIO[R, *], ZioStreams] {
  override val streams: capabilities.Streams[ZioStreams] = ZioStreams

  override def toRaw[RAW](serverRequest: ServerRequest, bodyType: RawBodyType[RAW]): Task[RawValue[RAW]] = bodyType match {
    case RawBodyType.StringBody(defaultCharset) => asByteArray(serverRequest).map(new String(_, defaultCharset)).map(RawValue(_))
    case RawBodyType.ByteArrayBody              => asByteArray(serverRequest).map(RawValue(_))
    case RawBodyType.ByteBufferBody             => asByteArray(serverRequest).map(bytes => ByteBuffer.wrap(bytes)).map(RawValue(_))
    case RawBodyType.InputStreamBody            => asByteArray(serverRequest).map(new ByteArrayInputStream(_)).map(RawValue(_))
    case RawBodyType.FileBody =>
      serverOptions.createFile(serverRequest).map(d => FileRange(d)).flatMap(file => ZIO.succeed(RawValue(file, Seq(file))))
    case RawBodyType.MultipartBody(_, _) => ZIO.never
  }

  override def toStream(serverRequest: ServerRequest): streams.BinaryStream = stream(serverRequest).asInstanceOf[streams.BinaryStream]

  private def stream(serverRequest: ServerRequest): Stream[Throwable, Byte] =
    zioHttpRequest((serverRequest)).body.asStream

  private def asByteArray(serverRequest: ServerRequest): Task[Array[Byte]] =
    zioHttpRequest(serverRequest).body.asArray

  private def zioHttpRequest(serverRequest: ServerRequest) = serverRequest.underlying.asInstanceOf[Request]
}
