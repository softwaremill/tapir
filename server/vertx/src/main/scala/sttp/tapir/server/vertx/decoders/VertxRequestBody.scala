package sttp.tapir.server.vertx.decoders

import io.vertx.core.Future
import io.vertx.core.buffer.Buffer
import io.vertx.core.streams.ReadStream
import io.vertx.ext.web.RoutingContext
import sttp.capabilities.Streams
import sttp.model.Part
import sttp.tapir.RawBodyType
import sttp.tapir.server.interpreter.RequestBody
import sttp.tapir.server.vertx.VertxServerOptions
import sttp.tapir.server.vertx.interpreters.FromVFuture
import sttp.tapir.server.vertx.streams.ReadStreamCompatible

import java.io.{ByteArrayInputStream, File}
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.file.{Files, Paths}
import java.util.Date
import scala.util.Random
import scala.collection.JavaConverters._

class VertxRequestBody[F[_], S: ReadStreamCompatible](
    rc: RoutingContext,
    serverOptions: VertxServerOptions[F],
    fromVFuture: FromVFuture[F]
) extends RequestBody[F, S] {
  override val streams: Streams[S] = ReadStreamCompatible[S].streams

  override def toRaw[R](bodyType: RawBodyType[R]): F[R] = fromVFuture(bodyType match {
    case RawBodyType.StringBody(defaultCharset) =>
      Future.succeededFuture(Option(rc.getBodyAsString(defaultCharset.toString)).getOrElse(""))
    case RawBodyType.ByteArrayBody =>
      Future.succeededFuture(Option(rc.getBody).fold(Array.emptyByteArray)(_.getBytes))
    case RawBodyType.ByteBufferBody =>
      Future.succeededFuture(Option(rc.getBody).fold(ByteBuffer.allocate(0))(_.getByteBuf.nioBuffer()))
    case RawBodyType.InputStreamBody =>
      val bytes = Option(rc.getBody).fold(Array.emptyByteArray)(_.getBytes)
      Future.succeededFuture(new ByteArrayInputStream(bytes))
    case RawBodyType.FileBody =>
      rc.fileUploads().asScala.headOption match {
        case Some(upload) =>
          Future.succeededFuture(new File(upload.uploadedFileName()))
        case None if rc.getBody != null =>
          val filePath = s"${serverOptions.uploadDirectory.getAbsolutePath}/tapir-${new Date().getTime}-${Random.nextLong()}"
          val fs = rc.vertx.fileSystem
          val result = fs
            .createFile(filePath)
            .flatMap(_ => fs.writeFile(filePath, rc.getBody))
            .flatMap(_ => Future.succeededFuture(new File(filePath)))
          result
        case None =>
          Future.failedFuture[File]("No body")
      }
    case RawBodyType.MultipartBody(partTypes, defaultType) =>
      val defaultParts = defaultType
        .fold(Map.empty[String, RawBodyType[_]]) { bodyType =>
          val files = rc.fileUploads().asScala.map(_.name())
          val form = rc.request().formAttributes().names().asScala

          (files ++ form)
            .diff(partTypes.keySet)
            .map(_ -> bodyType)
            .toMap
        }
      val allParts = defaultParts ++ partTypes
      Future.succeededFuture(
        allParts.map { case (partName, rawBodyType) =>
          Part(partName, extractPart(partName, rawBodyType))
        }.toSeq
      )
  })

  override def toStream(): streams.BinaryStream =
    ReadStreamCompatible[S].fromReadStream(rc.request.asInstanceOf[ReadStream[Buffer]]).asInstanceOf[streams.BinaryStream]

  private def extractPart[B](name: String, bodyType: RawBodyType[B]): B = {
    bodyType match {
      case RawBodyType.StringBody(charset) => new String(readBytes(name, rc, charset))
      case RawBodyType.ByteArrayBody       => readBytes(name, rc, Charset.defaultCharset())
      case RawBodyType.ByteBufferBody      => ByteBuffer.wrap(readBytes(name, rc, Charset.defaultCharset()))
      case RawBodyType.InputStreamBody     => throw new IllegalArgumentException("Cannot create a multipart as an InputStream")
      case RawBodyType.FileBody =>
        val f = rc.fileUploads.asScala.find(_.name == name).get
        new File(f.uploadedFileName())
      case RawBodyType.MultipartBody(partTypes, _) =>
        partTypes.map { case (partName, rawBodyType) =>
          Part(partName, extractPart(partName, rawBodyType))
        }.toSeq
    }
  }

  private def readBytes(name: String, rc: RoutingContext, charset: Charset) = {
    val formAttributes = rc.request.formAttributes

    val formBytes = Option(formAttributes.get(name)).map(_.getBytes(charset))
    val fileBytes = rc.fileUploads().asScala.find(_.name() == name).map { upload =>
      Files.readAllBytes(Paths.get(upload.uploadedFileName()))
    }

    formBytes.orElse(fileBytes).get
  }
}
