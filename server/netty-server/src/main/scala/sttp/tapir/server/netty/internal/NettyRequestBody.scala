package sttp.tapir.server.netty.internal

import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.{FullHttpRequest, HttpContent}
import io.netty.handler.codec.http.multipart.{FileUpload, HttpData, InterfaceHttpData}
import org.playframework.netty.http.StreamedHttpRequest
import org.reactivestreams.Publisher
import sttp.capabilities.Streams
import sttp.model.{HeaderNames, MediaType, Part}
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interpreter.{RawValue, RequestBody}
import sttp.tapir.server.model.InvalidMultipartBodyException
import sttp.tapir.server.netty.internal.reactivestreams.SubscriberInputStream
import sttp.tapir.{FileRange, InputStreamRange, RawBodyType, RawPart, TapirFile}

import java.io.{ByteArrayInputStream, InputStream}
import java.nio.ByteBuffer
import java.nio.file.Files

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

  /** Backend-specific way to process a multipart request into a raw value containing a sequence of raw parts. */
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

  /** Backend-specific way to write an array of bytes to a file. */
  def writeBytesToFile(bytes: Array[Byte], file: TapirFile): F[Unit]

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
      serverRequest.underlying match {
        case r: StreamedHttpRequest                                  => publisherToMultipart(r, serverRequest, m, maxBytes)
        case r if r.getClass().getSimpleName() == "EmptyHttpRequest" => monad.error(InvalidMultipartBodyException("Empty multipart body"))
        case _ => monad.error(new UnsupportedOperationException("Expected a streamed request for multipart body"))
      }
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
    data match {
      case httpData: HttpData  => toRawPartHttpData(serverRequest, httpData, partType)
      case unsupportedDataType =>
        monad.error(new UnsupportedOperationException(s"Unsupported multipart data type: $unsupportedDataType in part ${data.getName}"))
    }
  }

  private def toRawPartHttpData[R](
      serverRequest: ServerRequest,
      httpData: HttpData,
      partType: RawBodyType[R]
  ): F[Part[R]] = {
    // Note: Netty's multipart decoder does not expose other part headers, nor other disposition params.
    // Content-type is only exposed for file uploads.

    val partName = httpData.getName
    val (contentType, fileName) = httpData match {
      case fileUpload: FileUpload =>
        val contentType = Option(fileUpload.getContentType).flatMap(MediaType.parse(_).toOption)
        val fileName = Option(fileUpload.getFilename)
        contentType -> fileName
      case _ => (None, None)
    }

    partType match {
      case RawBodyType.StringBody(defaultCharset) =>
        val charset = Option(httpData.getCharset).getOrElse(defaultCharset)
        mapHttpData(httpData)(hd => Part(partName, hd.getString(charset), contentType = contentType, fileName = fileName))
      case RawBodyType.ByteArrayBody =>
        mapHttpData(httpData)(hd => Part(partName, hd.get(), contentType = contentType, fileName = fileName))
      case RawBodyType.ByteBufferBody =>
        mapHttpData(httpData)(hd => Part(partName, ByteBuffer.wrap(hd.get()), contentType = contentType, fileName = fileName))
      case RawBodyType.InputStreamBody =>
        val stream =
          if (httpData.isInMemory)
            new ByteArrayInputStream(httpData.get())
          else
            Files.newInputStream(httpData.getFile.toPath)

        monad.unit(Part(partName, stream, contentType = contentType, fileName = fileName))
      case RawBodyType.InputStreamRangeBody =>
        val stream =
          if (httpData.isInMemory)
            new ByteArrayInputStream(httpData.get())
          else
            Files.newInputStream(httpData.getFile.toPath)

        monad.unit(Part(partName, InputStreamRange(() => stream), contentType = contentType, fileName = fileName))
      case RawBodyType.FileBody =>
        val fileMonad =
          if (httpData.isInMemory)
            for {
              file <- createFile(serverRequest)
              _ <- writeBytesToFile(httpData.get(), file)
            } yield file
          else monad.unit(httpData.getFile)

        fileMonad.map(file => Part(partName, FileRange(file), contentType = contentType, fileName = fileName))
      case _: RawBodyType.MultipartBody =>
        monad.error(new UnsupportedOperationException(s"Nested multipart not supported, part name = $partName"))
    }
  }

  private def mapHttpData[T](httpData: HttpData)(f: HttpData => T): F[T] =
    if (httpData.isInMemory) monad.unit(f(httpData)) else monad.blocking(f(httpData))
}
