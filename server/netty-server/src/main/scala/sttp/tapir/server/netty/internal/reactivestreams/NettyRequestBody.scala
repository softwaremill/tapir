package sttp.tapir.server.netty.internal.reactivestreams

import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.{FullHttpRequest, HttpContent}
import org.playframework.netty.http.StreamedHttpRequest
import org.reactivestreams.Publisher
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interpreter.RequestBody
import sttp.tapir.RawBodyType
import sttp.tapir.TapirFile
import sttp.tapir.server.interpreter.RawValue
import sttp.tapir.FileRange
import sttp.tapir.InputStreamRange
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer

trait NettyRequestBody[F[_], S] extends RequestBody[F, S] {

  val DefaultChunkSize = 8192
  implicit def monad: MonadError[F]
  def createFile: ServerRequest => F[TapirFile]
  def publisherToBytes(publisher: Publisher[HttpContent], maxBytes: Option[Long]): F[Array[Byte]]
  def writeToFile(serverRequest: ServerRequest, file: TapirFile, maxBytes: Option[Long]): F[Unit]
  def publisherToStream(publisher: Publisher[HttpContent], maxBytes: Option[Long]): streams.BinaryStream
  def failedStream(e: => Throwable): streams.BinaryStream
  def emptyStream: streams.BinaryStream

  override def toRaw[RAW](serverRequest: ServerRequest, bodyType: RawBodyType[RAW], maxBytes: Option[Long]): F[RawValue[RAW]] = {
    bodyType match {
      case RawBodyType.StringBody(charset) => readAllBytes(serverRequest, maxBytes).map(bs => RawValue(new String(bs, charset)))
      case RawBodyType.ByteArrayBody =>
        readAllBytes(serverRequest, maxBytes).map(RawValue(_))
      case RawBodyType.ByteBufferBody =>
        readAllBytes(serverRequest, maxBytes).map(bs => RawValue(ByteBuffer.wrap(bs)))
      case RawBodyType.InputStreamBody =>
        readAllBytes(serverRequest, maxBytes).map(bs => RawValue(new ByteArrayInputStream(bs)))
      case RawBodyType.InputStreamRangeBody =>
        readAllBytes(serverRequest, maxBytes).map(bs => RawValue(InputStreamRange(() => new ByteArrayInputStream(bs))))
      case RawBodyType.FileBody =>
        for {
          file <- createFile(serverRequest)
          _ <- writeToFile(serverRequest, file, maxBytes)
        } yield RawValue(FileRange(file), Seq(FileRange(file)))
      case _: RawBodyType.MultipartBody => monad.error(new UnsupportedOperationException())
    }
  }

  override def toStream(serverRequest: ServerRequest, maxBytes: Option[Long]): streams.BinaryStream =
    serverRequest.underlying match {
      case r: FullHttpRequest if r.content() == Unpooled.EMPTY_BUFFER => // means EmptyHttpRequest, but that class is not public
        emptyStream
      case publisher: StreamedHttpRequest =>
        publisherToStream(publisher, maxBytes)
      case other =>
        failedStream(new UnsupportedOperationException(s"Unexpected Netty request of type: ${other.getClass().getName()}"))
    }

  // Used by different netty backends to handle raw body input
  def readAllBytes(serverRequest: ServerRequest, maxBytes: Option[Long]): F[Array[Byte]] =
    serverRequest.underlying match {
      case r: FullHttpRequest if r.content() == Unpooled.EMPTY_BUFFER =>
        monad.unit(Array[Byte](0))
      case req: StreamedHttpRequest =>
        publisherToBytes(req, maxBytes)
      case other => monad.error(new UnsupportedOperationException(s"Unexpected Netty request of type ${other.getClass().getName()}"))
    }
}
