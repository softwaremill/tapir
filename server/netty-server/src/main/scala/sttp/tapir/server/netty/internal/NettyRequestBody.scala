package sttp.tapir.server.netty.internal

import io.netty.buffer.{ByteBufInputStream, ByteBufUtil}
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.multipart.InterfaceHttpData.HttpDataType
import io.netty.handler.codec.http.multipart.{Attribute, FileUpload, HttpData, HttpPostMultipartRequestDecoder}
import sttp.capabilities
import sttp.model.{MediaType, Part}
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.capabilities.NoStreams
import sttp.tapir.internal.SequenceSupport
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interpreter.{RawValue, RequestBody}
import sttp.tapir.{FileRange, RawBodyType, RawPart, TapirFile}

import java.nio.ByteBuffer
import java.nio.file.Files
import scala.collection.JavaConverters._

class NettyRequestBody[F[_]](createFile: ServerRequest => F[TapirFile])(implicit
    monadError: MonadError[F]
) extends RequestBody[F, NoStreams] {

  override val streams: capabilities.Streams[NoStreams] = NoStreams

  override def toRaw[RAW](serverRequest: ServerRequest, bodyType: RawBodyType[RAW]): F[RawValue[RAW]] = {

    /** [[ByteBufUtil.getBytes(io.netty.buffer.ByteBuf)]] copies buffer without affecting reader index of the original. */
    def requestContentAsByteArray = ByteBufUtil.getBytes(nettyRequest(serverRequest).content())

    bodyType match {
      case RawBodyType.StringBody(charset) => monadError.unit(RawValue(nettyRequest(serverRequest).content().toString(charset)))
      case RawBodyType.ByteArrayBody       => monadError.unit(RawValue(requestContentAsByteArray))
      case RawBodyType.ByteBufferBody      => monadError.unit(RawValue(ByteBuffer.wrap(requestContentAsByteArray)))
      case RawBodyType.InputStreamBody     => monadError.unit(RawValue(new ByteBufInputStream(nettyRequest(serverRequest).content())))
      case RawBodyType.FileBody =>
        createFile(serverRequest)
          .map(file => {
            Files.write(file.toPath, requestContentAsByteArray)
            RawValue(FileRange(file), Seq(FileRange(file)))
          })
      case m: RawBodyType.MultipartBody =>
        monadError
          .unit(new HttpPostMultipartRequestDecoder(nettyRequest(serverRequest)))
          .flatMap(decoder => {
            getParts(serverRequest, m, decoder)
              .sequence()
              .map(RawValue.fromParts)
              .map(a => {
                decoder.destroy()
                a.asInstanceOf[RawValue[RAW]]
              })
          })
    }
  }

  private def nettyRequest(serverRequest: ServerRequest): FullHttpRequest = serverRequest.underlying.asInstanceOf[FullHttpRequest]

  private def getParts(
      serverRequest: ServerRequest,
      m: RawBodyType.MultipartBody,
      decoder: HttpPostMultipartRequestDecoder
  ): List[F[Part[Any]]] = {
    decoder.getBodyHttpDatas.asScala
      .flatMap(httpData =>
        httpData.getHttpDataType match {
          case HttpDataType.Attribute =>
            m.partType(httpData.getName).map(c => toPart(serverRequest, c, httpData.asInstanceOf[Attribute], None)).toList
          case HttpDataType.FileUpload =>
            m.partType(httpData.getName)
              .map(c => {
                val upload = httpData.asInstanceOf[FileUpload]
                toPart(serverRequest, c, upload, Some(upload.getContentType))
              })
              .toList
          case HttpDataType.InternalAttribute => throw new UnsupportedOperationException("DataType not supported")
        }
      )
      .toList
  }

  private def toPart(
      serverRequest: ServerRequest,
      m: RawBodyType[_],
      upload: HttpData,
      contentType: Option[String]
  ): F[Part[Any]] = {
    val mediaType = contentType.flatMap(c => MediaType.parse(c).toOption)
    m match {
      case RawBodyType.StringBody(charset) =>
        monadError.unit(Part(name = upload.getName, body = upload.getString(charset), contentType = mediaType))
      case RawBodyType.ByteBufferBody => monadError.unit(Part(name = upload.getName, body = upload.content(), contentType = mediaType))
      case RawBodyType.InputStreamBody =>
        monadError.unit(Part(name = upload.getName, body = new ByteBufInputStream(upload.content()), contentType = mediaType))
      case RawBodyType.ByteArrayBody => monadError.unit(Part(name = upload.getName, body = upload.get(), contentType = mediaType))
      case RawBodyType.FileBody =>
        createFile(serverRequest)
          .map(file => {
            Files.write(file.toPath, ByteBufUtil.getBytes(upload.content()))
            Part(
              name = upload.getName,
              body = FileRange(file),
              contentType = mediaType,
              fileName = Some(file.getName)
            )
          })
      case _ => throw new UnsupportedOperationException("BodyType not supported as FileUpload type")
    }
  }

  override def toStream(serverRequest: ServerRequest): streams.BinaryStream = throw new UnsupportedOperationException()
}
