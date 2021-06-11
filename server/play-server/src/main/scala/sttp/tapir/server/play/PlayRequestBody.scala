package sttp.tapir.server.play

import akka.stream.Materializer
import akka.util.ByteString
import play.api.mvc.{RawBuffer, Request}
import play.core.parsers.Multipart
import sttp.capabilities.Streams
import sttp.model.Part
import sttp.tapir.internal._
import sttp.tapir.server.interpreter.{RawValue, RequestBody}
import sttp.tapir.{RawBodyType, RawPart}

import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

private[play] class PlayRequestBody(request: Request[RawBuffer], serverOptions: PlayServerOptions)(implicit mat: Materializer)
    extends RequestBody[Future, Nothing] {

  override val streams: Streams[Nothing] = NoStreams

  override def toRaw[R](bodyType: RawBodyType[R]): Future[RawValue[R]] = {
    val body = request.body.asBytes().getOrElse(ByteString.apply(java.nio.file.Files.readAllBytes(request.body.asFile.toPath)))
    val charset = request.charset.map(Charset.forName)
    toRaw(bodyType, charset, body)
  }

  override def toStream(): streams.BinaryStream = throw new UnsupportedOperationException()

  private def toRaw[R](bodyType: RawBodyType[R], charset: Option[Charset], body: ByteString)(implicit
      mat: Materializer
  ): Future[RawValue[R]] = {
    bodyType match {
      case RawBodyType.StringBody(defaultCharset) => Future(RawValue(new String(body.toArray, charset.getOrElse(defaultCharset))))
      case RawBodyType.ByteArrayBody              => Future(RawValue(body.toArray))
      case RawBodyType.ByteBufferBody             => Future(RawValue(body.toByteBuffer))
      case RawBodyType.InputStreamBody            => Future(RawValue(new ByteArrayInputStream(body.toArray)))
      case RawBodyType.FileBody =>
        Future(java.nio.file.Files.write(serverOptions.temporaryFileCreator.create().path, body.toArray))
          .map { p =>
            val file = p.toFile
            RawValue(file, Seq(file))
          }
      case m: RawBodyType.MultipartBody => multiPartRequestToRawBody(request, m, body)
    }
  }

  private def multiPartRequestToRawBody[R](request: Request[RawBuffer], m: RawBodyType.MultipartBody, body: ByteString)(implicit
      mat: Materializer
  ): Future[RawValue[Seq[RawPart]]] = {
    val bodyParser = serverOptions.playBodyParsers.multipartFormData(
      Multipart.handleFilePartAsTemporaryFile(serverOptions.temporaryFileCreator)
    )
    bodyParser.apply(request).run(body).flatMap {
      case Left(_) =>
        Future.failed(new IllegalArgumentException("Unable to parse multipart form data.")) // TODO
      case Right(value) =>
        val dataParts = value.dataParts.map { case (key, value) =>
          toRaw(
            m.partType(key).get,
            charset(m.partType(key).get),
            ByteString(value.flatMap(_.getBytes).toArray)
          ).map(body => Part(key, body.value))
        }.toSeq

        val fileParts = value.files.map(f => {
          toRaw(
            m.partType(f.key).get,
            charset(m.partType(f.key).get),
            ByteString.apply(java.nio.file.Files.readAllBytes(f.ref.path))
          ).map(body =>
            Part(f.key, body.value, Map(f.key -> f.dispositionType, Part.FileNameDispositionParam -> f.filename), Nil)
              .asInstanceOf[RawPart]
          )
        })
        Future.sequence(dataParts ++ fileParts).map(RawValue.fromParts)
    }
  }
}
