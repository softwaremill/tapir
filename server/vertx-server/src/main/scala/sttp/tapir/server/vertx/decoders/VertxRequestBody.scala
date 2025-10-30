package sttp.tapir.server.vertx.decoders

import io.vertx.core.Future
import io.vertx.ext.web.{FileUpload, RoutingContext}
import sttp.capabilities.Streams
import sttp.model.{MediaType, Part}
import sttp.tapir.model.ServerRequest
import sttp.tapir.{FileRange, InputStreamRange, RawBodyType}
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
    serverOptions: VertxServerOptions[F],
    fromVFuture: FromVFuture[F]
)(implicit val readStreamCompatible: ReadStreamCompatible[S])
    extends RequestBody[F, S] {
  override val streams: Streams[S] = readStreamCompatible.streams

  // We can ignore maxBytes here, because vertx native body limit check is attached to endpoints by methods in sttp.tapir.server.vertx.handlers
  override def toRaw[R](serverRequest: ServerRequest, bodyType: RawBodyType[R], maxBytes: Option[Long]): F[RawValue[R]] = {
    val rc = routingContext(serverRequest)
    fromVFuture(bodyType match {
      case RawBodyType.StringBody(defaultCharset) =>
        Future.succeededFuture(RawValue(Option(rc.body().asString(defaultCharset.toString)).getOrElse("")))
      case RawBodyType.ByteArrayBody =>
        Future.succeededFuture(RawValue(Option(rc.body().buffer()).fold(Array.emptyByteArray)(_.getBytes)))
      case RawBodyType.ByteBufferBody =>
        Future.succeededFuture(RawValue(Option(rc.body().buffer()).fold(ByteBuffer.allocate(0))(_.getByteBuf.nioBuffer())))
      case RawBodyType.InputStreamBody =>
        val bytes = Option(rc.body().buffer()).fold(Array.emptyByteArray)(_.getBytes)
        Future.succeededFuture(RawValue(new ByteArrayInputStream(bytes)))
      case RawBodyType.InputStreamRangeBody =>
        val bytes = Option(rc.body().buffer()).fold(Array.emptyByteArray)(_.getBytes)
        Future.succeededFuture(RawValue(InputStreamRange(() => new ByteArrayInputStream(bytes))))
      case RawBodyType.FileBody =>
        rc.fileUploads().asScala.headOption match {
          case Some(upload) =>
            Future.succeededFuture {
              val file = FileRange(new File(upload.uploadedFileName()))
              RawValue(file, Seq(file))
            }
          case None if rc.body().buffer() != null =>
            val filePath = newTempFilePath()
            val fs = rc.vertx.fileSystem
            val result = fs
              .createFile(filePath)
              .flatMap(_ => fs.writeFile(filePath, rc.body().buffer()))
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
                .map(body =>
                  Part(fu.name(), body, contentType = MediaType.parse(fu.contentType()).toOption, fileName = Option(fu.fileName()))
                )
            }
            .toList
            .flatten

          RawValue.fromParts(formParts ++ fileParts)
        }
    })
  }

  override def toStream(serverRequest: ServerRequest, maxBytes: Option[Long]): streams.BinaryStream =
    readStreamCompatible
      .fromReadStream(routingContext(serverRequest).request, maxBytes)
      .asInstanceOf[streams.BinaryStream]

  private def extractStringPart[B](part: String, bodyType: RawBodyType[B]): Option[Any] = {
    bodyType match {
      case RawBodyType.StringBody(charset)  => Some(new String(part.getBytes(Charset.defaultCharset()), charset))
      case RawBodyType.ByteArrayBody        => Some(part.getBytes(Charset.defaultCharset()))
      case RawBodyType.ByteBufferBody       => Some(ByteBuffer.wrap(part.getBytes(Charset.defaultCharset())))
      case RawBodyType.InputStreamBody      => throw new IllegalArgumentException("Cannot create a multipart as an InputStream")
      case RawBodyType.InputStreamRangeBody => throw new IllegalArgumentException("Cannot create a multipart as an InputStream")
      case RawBodyType.FileBody             =>
        val file = Paths.get(newTempFilePath())
        Files.write(file, part.getBytes(Charset.defaultCharset()))
        Some(FileRange(file.toFile))
      case RawBodyType.MultipartBody(_, _) => None
    }
  }

  private def extractFilePart[B](fu: FileUpload, bodyType: RawBodyType[B]): Option[Any] = {
    bodyType match {
      case RawBodyType.StringBody(charset)  => Some(new String(readFileBytes(fu), charset))
      case RawBodyType.ByteArrayBody        => Some(readFileBytes(fu))
      case RawBodyType.ByteBufferBody       => Some(ByteBuffer.wrap(readFileBytes(fu)))
      case RawBodyType.InputStreamBody      => throw new IllegalArgumentException("Cannot create a multipart as an InputStream")
      case RawBodyType.InputStreamRangeBody => throw new IllegalArgumentException("Cannot create a multipart as an InputStream")
      case RawBodyType.FileBody             => Some(FileRange(new File(fu.uploadedFileName())))
      case RawBodyType.MultipartBody(_, _)  => None
    }
  }

  private def readFileBytes(fu: FileUpload) = Files.readAllBytes(Paths.get(fu.uploadedFileName()))

  private def routingContext(serverRequest: ServerRequest) = serverRequest.underlying.asInstanceOf[RoutingContext]

  private def newTempFilePath(): String = {
    s"${serverOptions.uploadDirectory.getAbsolutePath}/tapir-${new Date().getTime}-${Random.nextLong()}"
  }
}
