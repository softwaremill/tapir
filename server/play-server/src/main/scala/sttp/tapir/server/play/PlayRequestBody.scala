package sttp.tapir.server.play

import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{FileIO, Source}
import org.apache.pekko.util.ByteString
import play.api.mvc.{Request, Result}
import play.core.parsers.Multipart
import sttp.capabilities.pekko.PekkoStreams
import sttp.model.{Header, MediaType, Part}
import sttp.tapir.internal._
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interpreter.{RawValue, RequestBody}
import sttp.tapir.{FileRange, InputStreamRange, RawBodyType, RawPart}

import java.io.File
import java.nio.charset.Charset
import scala.concurrent.{ExecutionContext, Future}
import scala.collection.compat._

private[play] class PlayRequestBody(serverOptions: PlayServerOptions)(implicit
    mat: Materializer
) extends RequestBody[Future, PekkoStreams] {

  override val streams: PekkoStreams = PekkoStreams
  private val parsers = serverOptions.playBodyParsers
  private lazy val filePartHandler = Multipart.handleFilePartAsTemporaryFile(serverOptions.temporaryFileCreator)

  override def toRaw[R](serverRequest: ServerRequest, bodyType: RawBodyType[R], maxBytes: Option[Long]): Future[RawValue[R]] = {
    import mat.executionContext
    val request = playRequest(serverRequest)
    val charset = request.charset.map(Charset.forName)
    toRaw(request, bodyType, charset, () => request.body, None, maxBytes)
  }

  override def toStream(serverRequest: ServerRequest, maxBytes: Option[Long]): streams.BinaryStream = {
    val stream = playRequest(serverRequest).body
    maxBytes.map(PekkoStreams.limitBytes(stream, _)).getOrElse(stream)
  }

  private def toRaw[R](
      request: Request[PekkoStreams.BinaryStream],
      bodyType: RawBodyType[R],
      charset: Option[Charset],
      body: () => Source[ByteString, Any],
      bodyAsFile: Option[File],
      maxBytes: Option[Long]
  )(implicit
      mat: Materializer,
      ec: ExecutionContext
  ): Future[RawValue[R]] = {
    // playBodyParsers is used, so that the maxLength limits from Play configuration are applied
    def bodyAsByteString(): Future[ByteString] = {
      maxBytes
        .map(parsers.byteString(_))
        .getOrElse(parsers.byteString)
        .apply(request)
        .run(body())
        .flatMap {
          case Left(result) => Future.failed(new PlayBodyParserException(result))
          case Right(value) => Future.successful(value)
        }
    }
    bodyType match {
      case RawBodyType.StringBody(defaultCharset) =>
        bodyAsByteString().map(b => RawValue(b.decodeString(charset.getOrElse(defaultCharset))))
      case RawBodyType.ByteArrayBody   => bodyAsByteString().map(b => RawValue(b.toArrayUnsafe()))
      case RawBodyType.ByteBufferBody  => bodyAsByteString().map(b => RawValue(b.toByteBuffer))
      case RawBodyType.InputStreamBody => bodyAsByteString().map(b => RawValue(b.asInputStream))
      case RawBodyType.InputStreamRangeBody =>
        bodyAsByteString().map(b => RawValue(new InputStreamRange(() => b.asInputStream)))
      case RawBodyType.FileBody =>
        bodyAsFile match {
          case Some(file) =>
            val tapirFile = FileRange(file)
            Future.successful(RawValue(tapirFile, Seq(tapirFile)))
          case None =>
            val file = FileRange(serverOptions.temporaryFileCreator.create().toFile)
            maxBytes
              .map(parsers.file(file.file, _))
              .getOrElse(parsers.file(file.file))
              .apply(request)
              .run(body())
              .flatMap {
                case Left(result) => Future.failed(new PlayBodyParserException(result))
                case Right(_)     => Future.successful(RawValue(file, Seq(file)))
              }
        }
      case m: RawBodyType.MultipartBody => multiPartRequestToRawBody(request, m, body, maxBytes)
    }
  }

  private def multiPartRequestToRawBody(
      request: Request[PekkoStreams.BinaryStream],
      m: RawBodyType.MultipartBody,
      body: () => Source[ByteString, Any],
      maxBytes: Option[Long]
  )(implicit
      mat: Materializer,
      ec: ExecutionContext
  ): Future[RawValue[Seq[RawPart]]] = {
    val bodyParser = maxBytes.map(parsers.multipartFormData(filePartHandler, _)).getOrElse(parsers.multipartFormData(filePartHandler))
    bodyParser.apply(request).run(body()).flatMap {
      case Left(r) =>
        Future.failed(new PlayBodyParserException(r))
      case Right(value) =>
        val dataParts: Seq[Future[Option[Part[Any]]]] = value.dataParts.flatMap { case (key, value: scala.collection.Seq[String]) =>
          m.partType(key).map { partType =>
            val data = value.map(ByteString.apply).to(scala.collection.immutable.Seq)
            val contentLength = Header.contentLength(data.map(_.length.toLong).sum)
            toRaw(
              request.withHeaders(request.headers.replace(contentLength.name -> contentLength.value)),
              partType,
              charset(partType),
              () => Source(data),
              bodyAsFile = None,
              maxBytes = None
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
                Some(f.ref.toFile),
                maxBytes = None
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

class PlayBodyParserException(val result: Result) extends Exception
