package tapir.server.play

import java.io.ByteArrayInputStream
import java.nio.charset.Charset

import akka.stream.Materializer
import akka.util.ByteString
import play.api.mvc.{RawBuffer, Request}
import play.core.parsers.Multipart
import sttp.model.Part
import sttp.tapir.{ByteArrayValueType, ByteBufferValueType, FileValueType, InputStreamValueType, MultipartValueType, RawPart, RawValueType, StringValueType}

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
      case mvt: MultipartValueType => multiPartRequestToRawBody(request, mvt)
    }
  }

  private def multiPartRequestToRawBody[R](request: Request[RawBuffer], mvt: MultipartValueType)(
      implicit mat: Materializer
  ): Future[Seq[RawPart]] = {
    val bodyParser = serverOptions.playBodyParsers.multipartFormData(
      Multipart.handleFilePartAsTemporaryFile(serverOptions.temporaryFileCreator)
    )
    bodyParser.apply(request).run().flatMap {
      case Left(value) => Future.failed(new RuntimeException(s"TODO handle it $value")) // what to do here?
      case Right(value) =>
        Future.sequence(value.files.map(f => {
          apply(
            mvt.partCodecMeta(f.key).get.rawValueType,
            f.contentType.map(Charset.forName),
            request,
            ByteString.apply(java.nio.file.Files.readAllBytes(f.ref.path))
          ).map(body => Part(f.key, body, Map(f.key -> f.dispositionType), Seq.empty).asInstanceOf[RawPart])
        }))
    }
  }
}
