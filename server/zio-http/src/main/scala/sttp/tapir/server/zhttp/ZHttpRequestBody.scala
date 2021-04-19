package sttp.tapir.server.zhttp

import sttp.capabilities
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.server.interpreter.RequestBody
import sttp.tapir.{Defaults, RawBodyType}
import zhttp.http.{HttpData, Request}
import zio.stream.{Stream, ZStream}
import zio.{RIO, Task}

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer

class ZHttpRequestBody[R](request: Request) extends RequestBody[RIO[R, *], ZioStreams] {
  override val streams: capabilities.Streams[ZioStreams] = ZioStreams

  def asByteArray: Task[Array[Byte]] = request.data.content match {
    case HttpData.Empty => Task.succeed(Array.emptyByteArray)
    case HttpData.CompleteData(data) => Task(data.toArray)
    case HttpData.StreamData(data) => data.runCollect.map(_.toArray)
  }

  def toRaw[A](bodyType: RawBodyType[A]): Task[A] = bodyType match {
    case RawBodyType.StringBody(defaultCharset) =>
      asByteArray.map(new String(_, defaultCharset))
    case RawBodyType.ByteArrayBody =>
      asByteArray
    case RawBodyType.ByteBufferBody =>
      asByteArray.map(bytes => ByteBuffer.wrap(bytes))
    case RawBodyType.InputStreamBody =>
      asByteArray.map(new ByteArrayInputStream(_))
    case RawBodyType.FileBody =>
      Task.effect(Defaults.createTempFile())
    case RawBodyType.MultipartBody(_, _) =>
      Task.never
  }

  val stream: Stream[Throwable, Byte] = request.data.content match {
    case HttpData.Empty => ZStream.empty
    case HttpData.CompleteData(data) => ZStream.fromChunk(data)
    case HttpData.StreamData(stream) => stream
  }

  def toStream(): streams.BinaryStream = stream.asInstanceOf[streams.BinaryStream]
}
