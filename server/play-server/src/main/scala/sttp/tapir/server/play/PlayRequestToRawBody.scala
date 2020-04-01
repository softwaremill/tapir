package sttp.tapir.server.play

import java.io.ByteArrayInputStream
import java.nio.charset.Charset

import akka.stream.Materializer
import akka.util.ByteString
import play.api.mvc.{RawBuffer, Request}
import play.core.parsers.Multipart
import sttp.model.Part
import sttp.tapir.{
  ByteArrayValueType,
  ByteBufferValueType,
  FileValueType,
  InputStreamValueType,
  MultipartValueType,
  RawPart,
  RawValueType,
  StringValueType
}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PlayRequestToRawBody(serverOptions: PlayServerOptions) {
  def apply[R](rawBodyType: RawValueType[R], charset: Option[Charset], request: Request[RawBuffer], body: ByteString)(
      implicit mat: Materializer
  ): Future[R] = {
    rawBodyType match {
      case StringValueType(defaultCharset) => Future(new String(body.toArray, charset.getOrElse(defaultCharset)))
      case ByteArrayValueType              => Future(body.toArray)
      case ByteBufferValueType             => Future(body.toByteBuffer)
      case InputStreamValueType            => Future(body.toArray).map(new ByteArrayInputStream(_))
      case FileValueType =>
        Future(java.nio.file.Files.write(serverOptions.temporaryFileCreator.create().path, body.toArray))
          .map(p => p.toFile)
      case mvt: MultipartValueType => multiPartRequestToRawBody(request, mvt, body)
    }
  }

  private def multiPartRequestToRawBody[R](request: Request[RawBuffer], mvt: MultipartValueType, body: ByteString)(
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
              mvt.partCodecMeta(key).get.rawValueType,
              mvt.partCodecMeta(key).get.format.mediaType.charset.map(Charset.forName),
              request,
              ByteString(value.flatMap(_.getBytes).toArray)
            ).map(body => Part(key, body).asInstanceOf[RawPart])
        }.toSeq

        val fileParts = value.files.map(f => {
          apply(
            mvt.partCodecMeta(f.key).get.rawValueType,
            mvt.partCodecMeta(f.key).get.format.mediaType.charset.map(Charset.forName),
            request,
            ByteString.apply(java.nio.file.Files.readAllBytes(f.ref.path))
          ).map(
            body =>
              Part(f.key, body, Map(f.key -> f.dispositionType, Part.FileNameDispositionParam -> f.filename), Seq.empty)
                .asInstanceOf[RawPart]
          )
        })
        Future.sequence(dataParts ++ fileParts)
    }
  }
}
