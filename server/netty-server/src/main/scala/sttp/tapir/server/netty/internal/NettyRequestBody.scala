package sttp.tapir.server.netty.internal

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
import sttp.capabilities.Streams

/** Common logic for processing request body in all Netty backends. It requires particular backends to implement a few operations. */
private[netty] trait NettyRequestBody[F[_], S <: Streams[S]] extends RequestBody[F, S] {

  implicit def monad: MonadError[F]

  /** Backend-specific implementation for creating a file. */
  def createFile: ServerRequest => F[TapirFile]

  /** Backend-specific way to process all elements emitted by a Publisher[HttpContent] into a raw array of bytes.
    *
    * @param publisher
    *   reactive publisher emitting byte chunks.
    * @param maxBytes
    *   optional request length limit. If exceeded, The effect `F` is failed with a [[sttp.capabilities.StreamMaxLengthExceededException]]
    * @return
    *   An effect which finishes with a single array of all collected bytes.
    */
  def publisherToBytes(publisher: Publisher[HttpContent], maxBytes: Option[Long]): F[Array[Byte]]

  /** Backend-specific way to process all elements emitted by a Publisher[HttpContent] and write their bytes into a file.
    *
    * @param serverRequest
    *   can have underlying `Publisher[HttpContent]` or an empty `FullHttpRequest`
    * @param file
    *   an empty file where bytes should be stored.
    * @param maxBytes
    *   optional request length limit. If exceeded, The effect `F` is failed with a [[sttp.capabilities.StreamMaxLengthExceededException]]
    * @return
    *   an effect which finishes when all data is written to the file.
    */
  def writeToFile(serverRequest: ServerRequest, file: TapirFile, maxBytes: Option[Long]): F[Unit]

  override def toRaw[RAW](serverRequest: ServerRequest, bodyType: RawBodyType[RAW], maxBytes: Option[Long]): F[RawValue[RAW]] = {
    bodyType match {
      case RawBodyType.StringBody(charset) => readAllBytes(serverRequest, maxBytes).map(bs => RawValue(new String(bs, charset)))
      case RawBodyType.ByteArrayBody =>
        readAllBytes(serverRequest, maxBytes).map(RawValue(_))
      case RawBodyType.ByteBufferBody =>
        readAllBytes(serverRequest, maxBytes).map(bs => RawValue(ByteBuffer.wrap(bs)))
      case RawBodyType.InputStreamBody =>
        // Possibly can be optimized to avoid loading all data eagerly into memory
        readAllBytes(serverRequest, maxBytes).map(bs => RawValue(new ByteArrayInputStream(bs)))
      case RawBodyType.InputStreamRangeBody =>
        // Possibly can be optimized to avoid loading all data eagerly into memory
        readAllBytes(serverRequest, maxBytes).map(bs => RawValue(InputStreamRange(() => new ByteArrayInputStream(bs))))
      case RawBodyType.FileBody =>
        for {
          file <- createFile(serverRequest)
          _ <- writeToFile(serverRequest, file, maxBytes)
        } yield RawValue(FileRange(file), Seq(FileRange(file)))
      case _: RawBodyType.MultipartBody => monad.error(new UnsupportedOperationException())
    }
  }

  private def readAllBytes(serverRequest: ServerRequest, maxBytes: Option[Long]): F[Array[Byte]] =
    serverRequest.underlying match {
      case r: FullHttpRequest if r.content() == Unpooled.EMPTY_BUFFER => // Empty request
        monad.unit(Array.empty[Byte])
      case req: StreamedHttpRequest =>
        publisherToBytes(req, maxBytes)
      case other => monad.error(new UnsupportedOperationException(s"Unexpected Netty request of type ${other.getClass().getName()}"))
    }
}
