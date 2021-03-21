package sttp.tapir.server.vertx.decoders

import io.vertx.core.Future
import io.vertx.core.buffer.Buffer
import io.vertx.core.streams.ReadStream
import io.vertx.ext.web.{FileUpload, RoutingContext}
import sttp.capabilities.Streams
import sttp.model.Part
import sttp.tapir.{FileRange, RawBodyType}
import sttp.tapir.server.interpreter.{RawValue, RequestBody}
import sttp.tapir.server.vertx.VertxServerOptions
import sttp.tapir.server.vertx.interpreters.FromVFuture
import sttp.tapir.server.vertx.streams.ReadStreamCompatible

import java.io.{ByteArrayInputStream, File}
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.file.{Files, Paths}
import java.util.Date
import scala.collection.JavaConverters._
import scala.util.Random

class VertxRequestBody[F[_], S <: Streams[S]](
    rc: RoutingContext,
    serverOptions: VertxServerOptions[F],
    fromVFuture: FromVFuture[F]
)(implicit val readStreamCompatible: ReadStreamCompatible[S])
    extends RequestBody[F, S] {
  override val streams: Streams[S] = readStreamCompatible.streams

  override def toRaw[R](bodyType: RawBodyType[R]): F[RawValue[R]] = fromVFuture(bodyType match {
    case RawBodyType.StringBody(defaultCharset) =>
      val str = rc.getBodyAsString(defaultCharset.toString)
      Future.succeededFuture(RawValue(Option(str).getOrElse("")))
    case RawBodyType.ByteArrayBody =>
      Future.succeededFuture(RawValue(Option(rc.getBody).fold(Array.emptyByteArray)(_.getBytes)))
    case RawBodyType.ByteBufferBody =>
      Future.succeededFuture(RawValue(Option(rc.getBody).fold(ByteBuffer.allocate(0))(buffer => buffer.getByteBuf.nioBuffer())))
    case RawBodyType.InputStreamBody =>
      val bytes = Option(rc.getBody).fold(Array.emptyByteArray)(_.getBytes)
      Future.succeededFuture(RawValue(new ByteArrayInputStream(bytes)))
    case RawBodyType.FileBody =>
      rc.fileUploads().asScala.headOption match {
        case Some(upload) =>
          Future.succeededFuture {
            val file = FileRange(new File(upload.uploadedFileName()))
            RawValue(file, Seq(file))
          }
        case None if rc.getBody != null =>
          val filePath = s"${serverOptions.uploadDirectory.getAbsolutePath}/tapir-${new Date().getTime}-${Random.nextLong()}"
          val fs = rc.vertx.fileSystem
          val result = fs
            .createFile(filePath)
            .flatMap(_ => fs.writeFile(filePath, rc.getBody))
            .flatMap(_ =>
              Future.succeededFuture {
                val file = FileRange(new File(filePath))
                RawValue(file, Seq(file))
              }
            )
          result
        case None =>
          Future.failedFuture[RawValue[FileRange]]("No body")
      }
    case mp: RawBodyType.MultipartBody =>
      Future.succeededFuture {
        val formParts = rc
          .request()
          .formAttributes()
          .entries()
          .asScala
          .map { e =>
            mp.partType(e.getKey)
              .flatMap(rawBodyType => extractStringPart(e.getValue, rawBodyType))
              .map(body => Part(e.getKey, body))
          }
          .toList
          .flatten

        val fileParts = rc
          .fileUploads()
          .asScala
          .map { fu =>
            mp.partType(fu.name())
              .flatMap(rawBodyType => extractFilePart(fu, rawBodyType))
              .map(body => Part(fu.name(), body, fileName = Option(fu.fileName())))
          }
          .toList
          .flatten

        RawValue.fromParts(formParts ++ fileParts)
      }
  })

  override def toStream(): streams.BinaryStream =
    readStreamCompatible.fromReadStream(rc.request.asInstanceOf[ReadStream[Buffer]]).asInstanceOf[streams.BinaryStream]

  private def extractStringPart[B](part: String, bodyType: RawBodyType[B]): Option[Any] = {
    bodyType match {
      case RawBodyType.StringBody(charset) => Some(new String(part.getBytes(Charset.defaultCharset()), charset))
      case RawBodyType.ByteArrayBody       => Some(part.getBytes(Charset.defaultCharset()))
      case RawBodyType.ByteBufferBody      => Some(ByteBuffer.wrap(part.getBytes(Charset.defaultCharset())))
      case RawBodyType.InputStreamBody     => throw new IllegalArgumentException("Cannot create a multipart as an InputStream")
      case RawBodyType.FileBody            => None
      case RawBodyType.MultipartBody(_, _) => None
    }
  }

  private def extractFilePart[B](fu: FileUpload, bodyType: RawBodyType[B]): Option[Any] = {
    bodyType match {
      case RawBodyType.StringBody(charset) => Some(new String(readFileBytes(fu), charset))
      case RawBodyType.ByteArrayBody       => Some(readFileBytes(fu))
      case RawBodyType.ByteBufferBody      => Some(ByteBuffer.wrap(readFileBytes(fu)))
      case RawBodyType.InputStreamBody     => throw new IllegalArgumentException("Cannot create a multipart as an InputStream")
      case RawBodyType.FileBody            => Some(FileRange(new File(fu.uploadedFileName())))
      case RawBodyType.MultipartBody(_, _) => None
    }
  }

  private def readFileBytes(fu: FileUpload) = {
    Files.readAllBytes(Paths.get(fu.uploadedFileName()))
  }
}
