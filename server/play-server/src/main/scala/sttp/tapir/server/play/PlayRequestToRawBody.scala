package sttp.tapir.server.play

import java.io.ByteArrayInputStream
import java.nio.charset.Charset

import akka.stream.Materializer
import akka.util.ByteString
import play.api.mvc.{RawBuffer, Request}
import play.core.parsers.Multipart
import sttp.model.Part
import sttp.tapir.{RawBodyType, RawPart}
import sttp.tapir.internal._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PlayRequestToRawBody(serverOptions: PlayServerOptions) {
  def apply[R](bodyType: RawBodyType[R], charset: Option[Charset], request: Request[RawBuffer], body: ByteString)(
      implicit mat: Materializer
  ): Future[R] = {
    bodyType match {
      case RawBodyType.StringBody(defaultCharset) => Future(new String(body.toArray, charset.getOrElse(defaultCharset)))
      case RawBodyType.ByteArrayBody              => Future(body.toArray)
      case RawBodyType.ByteBufferBody             => Future(body.toByteBuffer)
      case RawBodyType.InputStreamBody            => Future(body.toArray).map(new ByteArrayInputStream(_))
      case RawBodyType.FileBody =>
        Future(java.nio.file.Files.write(serverOptions.temporaryFileCreator.create().path, body.toArray))
          .map(p => p.toFile)
      case m: RawBodyType.MultipartBody => multiPartRequestToRawBody(request, m, body)
    }
  }

  private def multiPartRequestToRawBody[R](request: Request[RawBuffer], m: RawBodyType.MultipartBody, body: ByteString)(
      implicit mat: Materializer
  ): Future[Seq[RawPart]] = {
    val bodyParser = serverOptions.playBodyParsers.multipartFormData(
      Multipart.handleFilePartAsTemporaryFile(serverOptions.temporaryFileCreator)
    )
    bodyParser.apply(request).run(body).flatMap {
      case Left(_) =>
        Future.failed(new IllegalArgumentException("Unable to parse multipart form data.")) // TODO
      case Right(value) =>
        val dataParts = value.dataParts.map {
          case (key, value) =>
            apply(
              m.partType(key).get,
              charset(m.partType(key).get),
              request,
              ByteString(value.flatMap(_.getBytes).toArray)
            ).map(body => Part(key, body).asInstanceOf[RawPart])
        }.toSeq

        val fileParts = value.files.map(f => {
          apply(
            m.partType(f.key).get,
            charset(m.partType(f.key).get),
            request,
            ByteString.apply(java.nio.file.Files.readAllBytes(f.ref.path))
          ).map(body =>
            Part(f.key, body, Map(f.key -> f.dispositionType, Part.FileNameDispositionParam -> f.filename), Seq.empty)
              .asInstanceOf[RawPart]
          )
        })
        Future.sequence(dataParts ++ fileParts)
    }
  }
}
