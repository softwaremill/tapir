package sttp.tapir.server.netty.internal

import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.{FullHttpRequest, HttpContent}
import org.playframework.netty.http.StreamedHttpRequest
import org.reactivestreams.Publisher
import sttp.capabilities.Streams
import sttp.model.HeaderNames
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interpreter.{RawValue, RequestBody}
import sttp.tapir.server.netty.internal.reactivestreams.SubscriberInputStream
import sttp.tapir.{FileRange, InputStreamRange, RawBodyType, TapirFile}

import java.io.InputStream
import java.nio.ByteBuffer

import scala.collection.JavaConverters._
import sttp.tapir.RawPart
import io.netty.handler.codec.http.multipart.InterfaceHttpData
import sttp.model.Part
import io.netty.handler.codec.http.multipart.HttpData
import io.netty.handler.codec.http.multipart.FileUpload
import java.io.ByteArrayInputStream
import java.io.File

/** Common logic for processing request body in all Netty backends. It requires particular backends to implement a few operations. */
private[netty] trait NettyRequestBody[F[_], S <: Streams[S]] extends RequestBody[F, S] {

  implicit def monad: MonadError[F]

  /** Backend-specific implementation for creating a file. */
  def createFile: ServerRequest => F[TapirFile]

  /** Backend-specific way to process all elements emitted by a Publisher[HttpContent] into a raw array of bytes.
    *
    * @param publisher
    *   reactive publisher emitting byte chunks.
    * @param contentLength
    *   Total content length, if known
    * @param maxBytes
    *   optional request length limit. If exceeded, The effect `F` is failed with a [[sttp.capabilities.StreamMaxLengthExceededException]]
    * @return
    *   An effect which finishes with a single array of all collected bytes.
    */
  def publisherToBytes(publisher: Publisher[HttpContent], contentLength: Option[Long], maxBytes: Option[Long]): F[Array[Byte]]

  /** Reads the reactive stream emitting HttpData into a vector of parts. Implementation-specific, as file manipulations and stream
    * processing logic can be different for different backends.
    */
  def publisherToMultipart(
      nettyRequest: StreamedHttpRequest,
      serverRequest: ServerRequest,
      m: RawBodyType.MultipartBody,
      maxBytes: Option[Long]
  ): F[RawValue[Seq[RawPart]]]

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

  def writeBytesToFile(bytes: Array[Byte], file: File): F[Unit]

  override def toRaw[RAW](
      serverRequest: ServerRequest,
      bodyType: RawBodyType[RAW],
      maxBytes: Option[Long]
  ): F[RawValue[RAW]] = bodyType match {
    case RawBodyType.StringBody(charset) =>
      readAllBytes(serverRequest, maxBytes).map(bs => RawValue(new String(bs, charset)))
    case RawBodyType.ByteArrayBody =>
      readAllBytes(serverRequest, maxBytes).map(RawValue(_))
    case RawBodyType.ByteBufferBody =>
      readAllBytes(serverRequest, maxBytes).map(bs => RawValue(ByteBuffer.wrap(bs)))
    case RawBodyType.InputStreamBody =>
      monad.eval(RawValue(readAsStream(serverRequest, maxBytes)))
    case RawBodyType.InputStreamRangeBody =>
      monad.unit(RawValue(InputStreamRange(() => readAsStream(serverRequest, maxBytes))))
    case RawBodyType.FileBody =>
      for {
        file <- createFile(serverRequest)
        _ <- writeToFile(serverRequest, file, maxBytes)
      } yield RawValue(FileRange(file), Seq(FileRange(file)))
    case m: RawBodyType.MultipartBody =>
      publisherToMultipart(serverRequest.underlying.asInstanceOf[StreamedHttpRequest], serverRequest, m, maxBytes)
  }

  private def readAllBytes(serverRequest: ServerRequest, maxBytes: Option[Long]): F[Array[Byte]] =
    serverRequest.underlying match {
      case r: FullHttpRequest if r.content() == Unpooled.EMPTY_BUFFER => // Empty request
        monad.unit(Array.empty[Byte])
      case req: StreamedHttpRequest =>
        val contentLength = Option(req.headers().get(HeaderNames.ContentLength)).map(_.toLong)
        publisherToBytes(req, contentLength, maxBytes)
      case other =>
        monad.error(new UnsupportedOperationException(s"Unexpected Netty request of type ${other.getClass.getName}"))
    }

  private def readAsStream(serverRequest: ServerRequest, maxBytes: Option[Long]): InputStream = {
    serverRequest.underlying match {
      case r: FullHttpRequest if r.content() == Unpooled.EMPTY_BUFFER => // Empty request
        InputStream.nullInputStream()
      case req: StreamedHttpRequest =>
        val contentLength = Option(req.headers().get(HeaderNames.ContentLength)).map(_.toLong)
        SubscriberInputStream.processAsStream(req, contentLength, maxBytes)
      case other =>
        throw new UnsupportedOperationException(s"Unexpected Netty request of type ${other.getClass.getName}")
    }
  }

  protected def toRawPart[R](
      serverRequest: ServerRequest,
      data: InterfaceHttpData,
      partType: RawBodyType[R]
  ): F[Part[R]] = {
    val partName = data.getName()
    data match {
      case httpData: HttpData =>
        // TODO filename* attribute is not used by netty. Non-ascii filenames like https://github.com/http4s/http4s/issues/5809 are unsupported.
        toRawPartHttpData(partName, serverRequest, httpData, partType)
      case unsupportedDataType =>
        monad.error(new UnsupportedOperationException(s"Unsupported multipart data type: $unsupportedDataType in part $partName"))
    }
  }

  private def toRawPartHttpData[R](
      partName: String,
      serverRequest: ServerRequest,
      httpData: HttpData,
      partType: RawBodyType[R]
  ): F[Part[R]] = {
    val fileName = httpData match {
      case fileUpload: FileUpload => Option(fileUpload.getFilename())
      case _                      => None
    }
    partType match {
      case RawBodyType.StringBody(defaultCharset) =>
        // TODO otherDispositionParams not supported. They are normally a part of the content-disposition part header, but this header is not directly accessible, they are extracted internally by the decoder.
        val charset = if (httpData.getCharset() != null) httpData.getCharset() else defaultCharset
        readHttpData(httpData, _.getString(charset)).map(body => Part(partName, body, fileName = fileName))
      case RawBodyType.ByteArrayBody =>
        readHttpData(httpData, _.get()).map(body => Part(partName, body, fileName = fileName))
      case RawBodyType.ByteBufferBody =>
        readHttpData(httpData, _.get()).map(body => Part(partName, ByteBuffer.wrap(body), fileName = fileName))
      case RawBodyType.InputStreamBody =>
        (if (httpData.isInMemory())
           monad.unit(new ByteArrayInputStream(httpData.get()))
         else {
           monad.blocking(java.nio.file.Files.newInputStream(httpData.getFile().toPath()))
         }).map(body => Part(partName, body, fileName = fileName))
      case RawBodyType.InputStreamRangeBody =>
        val body = () => {
          if (httpData.isInMemory())
            new ByteArrayInputStream(httpData.get())
          else
            java.nio.file.Files.newInputStream(httpData.getFile().toPath())
        }
        monad.unit(Part(partName, InputStreamRange(body), fileName = fileName))
      case RawBodyType.FileBody =>
        val fileF: F[File] =
          if (httpData.isInMemory())
            (for {
              file <- createFile(serverRequest)
              _ <- writeBytesToFile(httpData.get(), file)
            } yield file)
          else monad.unit(httpData.getFile())
        fileF.map(file => Part(partName, FileRange(file), fileName = fileName))
      case _: RawBodyType.MultipartBody =>
        monad.error(new UnsupportedOperationException(s"Nested multipart not supported, part name = $partName"))
    }
  }

  private def readHttpData[T](httpData: HttpData, f: HttpData => T): F[T] =
    if (httpData.isInMemory())
      monad.unit(f(httpData))
    else
      monad.blocking(f(httpData))
}
