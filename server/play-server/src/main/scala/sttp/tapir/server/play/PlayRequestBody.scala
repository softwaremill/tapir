package sttp.tapir.server.play

import akka.stream.Materializer
import akka.stream.scaladsl.{FileIO, Sink, Source}
import akka.util.ByteString
import play.api.mvc.Request
import play.core.parsers.Multipart
import sttp.capabilities.akka.AkkaStreams
import sttp.model.{Header, MediaType, Part}
import sttp.tapir.internal._
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interpreter.{RawValue, RequestBody}
import sttp.tapir.{FileRange, RawBodyType, RawPart}

import java.io.{ByteArrayInputStream, File}
import java.nio.charset.Charset
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

private[play] class PlayRequestBody(serverOptions: PlayServerOptions)(implicit
    mat: Materializer
) extends RequestBody[Future, AkkaStreams] {

  override val streams: AkkaStreams = AkkaStreams

  override def toRaw[R](serverRequest: ServerRequest, bodyType: RawBodyType[R]): Future[RawValue[R]] = {
    val request = playRequest(serverRequest)
    val charset = request.charset.map(Charset.forName)
    toRaw(request, bodyType, charset, () => request.body, None)
  }

  override def toStream(serverRequest: ServerRequest): streams.BinaryStream = playRequest(serverRequest).body

  private def toRaw[R](
      request: Request[AkkaStreams.BinaryStream],
      bodyType: RawBodyType[R],
      charset: Option[Charset],
      body: () => Source[ByteString, Any],
      bodyAsFile: Option[File]
  )(implicit
      mat: Materializer
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
          case Some(file) =>
            val tapirFile = FileRange(file)
            Future.successful(RawValue(tapirFile, Seq(tapirFile)))
          case None =>
            val file = FileRange(serverOptions.temporaryFileCreator.create().toFile)
            body().runWith(FileIO.toPath(file.file.toPath)).map(_ => RawValue(file, Seq(file)))
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
        val dataParts: Seq[Future[Option[Part[Any]]]] = value.dataParts.flatMap { case (key, value) =>
          m.partType(key).map { partType =>
            toRaw(
              request,
              partType,
              charset(partType),
              () => Source.single(ByteString(value.flatMap(_.getBytes).toArray)),
              None
            ).map(body => Some(Part(key, body.value)))
          }
        }.toSeq

        val fileParts: Seq[Future[Option[Part[Any]]]] = value.files.map { f =>
          m.partType(f.key)
            .map { partType =>
              toRaw(
                request,
                partType,
                charset(partType),
                () => FileIO.fromPath(f.ref.path),
                Some(f.ref.toFile)
              ).map(body =>
                Some(
                  Part(
                    f.key,
                    body.value,
                    Map(f.key -> f.dispositionType, Part.FileNameDispositionParam -> f.filename),
                    f.contentType.flatMap(MediaType.parse(_).toOption).map(Header.contentType).toList
                  )
                    .asInstanceOf[RawPart]
                )
              )
            }
            .getOrElse {
              serverOptions.deleteFile(f.ref.toFile).map(_ => Option.empty)
            }
        }
        Future.sequence(dataParts ++ fileParts).map(ps => ps.collect { case Some(p) => p }).map(RawValue.fromParts)
    }
  }

  private def playRequest(serverRequest: ServerRequest) = serverRequest.underlying.asInstanceOf[Request[Source[ByteString, Any]]]
}
