package sttp.tapir.server.ziohttp

import sttp.capabilities
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.{FileRange, RawBodyType}
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interpreter.{RawValue, RequestBody}
import zio.stream.{Stream, ZStream}
import zio.{RIO, Task}

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import zio.Chunk
import zhttp.http.Request

class ZioHttpRequestBody[R](request: Request, serverRequest: ServerRequest, serverOptions: ZioHttpServerOptions[R])
    extends RequestBody[RIO[R, *], ZioStreams] {
  override val streams: capabilities.Streams[ZioStreams] = ZioStreams

  private def asByteArray: Task[Array[Byte]] = request.getBody.map(_.toArray)

  override def toRaw[RAW](bodyType: RawBodyType[RAW]): Task[RawValue[RAW]] = bodyType match {
    case RawBodyType.StringBody(defaultCharset) => asByteArray.map(new String(_, defaultCharset)).map(RawValue(_))
    case RawBodyType.ByteArrayBody              => asByteArray.map(RawValue(_))
    case RawBodyType.ByteBufferBody             => asByteArray.map(bytes => ByteBuffer.wrap(bytes)).map(RawValue(_))
    case RawBodyType.InputStreamBody            => asByteArray.map(new ByteArrayInputStream(_)).map(RawValue(_))
    case RawBodyType.FileBody =>
      serverOptions.createFile(serverRequest).map(d => FileRange(d)).flatMap(file => Task.succeed(RawValue(file, Seq(file))))
    case RawBodyType.MultipartBody(_, _) => Task.never
  }

  private def stream: Stream[Throwable, Byte] = ZStream.fromEffect(request.getBody).flatMap((e: Chunk[Byte]) => ZStream.fromChunk(e))

  override def toStream(): streams.BinaryStream = stream.asInstanceOf[streams.BinaryStream]
}
