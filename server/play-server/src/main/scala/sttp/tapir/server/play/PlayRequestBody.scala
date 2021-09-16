package sttp.tapir.server.play

import akka.stream.Materializer
import akka.stream.scaladsl.{FileIO, Sink, Source}
import akka.util.{ByteString, ByteStringBuilder}
import play.api.mvc.Request
import play.core.parsers.Multipart
import sttp.capabilities.akka.AkkaStreams
import sttp.model.Part
import sttp.tapir.internal._
import sttp.tapir.server.interpreter.{RawValue, RequestBody}
import sttp.tapir.{RawBodyType, RawPart}

import java.io.{ByteArrayInputStream, File}
import java.nio.charset.Charset
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

private[play] class PlayRequestBody(request: Request[Source[ByteString, Any]], serverOptions: PlayServerOptions)(implicit
    mat: Materializer
) extends RequestBody[Future, AkkaStreams] {

  override val streams: AkkaStreams = AkkaStreams

  override def toRaw[R](bodyType: RawBodyType[R]): Future[RawValue[R]] = {
    val charset = request.charset.map(Charset.forName)
    toRaw(bodyType, charset, () => request.body, None)
  }

  override def toStream(): streams.BinaryStream = {
    request.body
  }

  private def toRaw[R](bodyType: RawBodyType[R], charset: Option[Charset], body: () => Source[ByteString, Any], bodyAsFile: Option[File])(
      implicit mat: Materializer
  ): Future[RawValue[R]] = {
    def bodyAsByteString() = body().runWith(Sink.fold(ByteString.newBuilder)(_ append _)).map(_.result())
    bodyType match {
      case RawBodyType.StringBody(defaultCharset) =>
        bodyAsByteString().map(b => RawValue(new String(b.toArray, charset.getOrElse(defaultCharset))))
      case RawBodyType.ByteArrayBody   => bodyAsByteString().map(b => RawValue(b.toArray))
      case RawBodyType.ByteBufferBody  => bodyAsByteString().map(b => RawValue(b.toByteBuffer))
      case RawBodyType.InputStreamBody => bodyAsByteString().map(b => RawValue(new ByteArrayInputStream(b.toArray)))
      case RawBodyType.FileBody =>
        bodyAsFile match {
          case Some(file) => Future.successful(RawValue(file, Seq(file)))
          case None =>
            val file = serverOptions.temporaryFileCreator.create().toFile
            body().runWith(FileIO.toPath(file.toPath)).map(_ => RawValue(file, Seq(file)))
        }
      case m: RawBodyType.MultipartBody => multiPartRequestToRawBody(request, m, body)
    }
  }

  private def multiPartRequestToRawBody(
      request: Request[AkkaStreams.BinaryStream],
      m: RawBodyType.MultipartBody,
      body: () => Source[ByteString, Any]
  )(implicit
      mat: Materializer
  ): Future[RawValue[Seq[RawPart]]] = {
    val bodyParser = serverOptions.playBodyParsers.multipartFormData(
      Multipart.handleFilePartAsTemporaryFile(serverOptions.temporaryFileCreator)
    )
    bodyParser.apply(request).run(body()).flatMap {
      case Left(_) =>
        Future.failed(new IllegalArgumentException("Unable to parse multipart form data.")) // TODO
      case Right(value) =>
        val dataParts = value.dataParts.map { case (key, value) =>
          toRaw(
            m.partType(key).get,
            charset(m.partType(key).get),
            () => Source.single(ByteString(value.flatMap(_.getBytes).toArray)),
            None
          ).map(body => Part(key, body.value))
        }.toSeq

        val fileParts = value.files.map(f => {
          toRaw(
            m.partType(f.key).get,
            charset(m.partType(f.key).get),
            () => FileIO.fromPath(f.ref.path),
            Some(f.ref.toFile)
          ).map(body =>
            Part(f.key, body.value, Map(f.key -> f.dispositionType, Part.FileNameDispositionParam -> f.filename), Nil)
              .asInstanceOf[RawPart]
          )
        })
        Future.sequence(dataParts ++ fileParts).map(RawValue.fromParts)
    }
  }
}
