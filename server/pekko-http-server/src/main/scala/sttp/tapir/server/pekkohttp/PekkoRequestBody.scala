package sttp.tapir.server.pekkohttp

import org.apache.pekko.http.scaladsl.model.{EntityStreamSizeException, HttpEntity, Multipart, ParsingException, RequestEntity}
import org.apache.pekko.http.scaladsl.server.RequestContext
import org.apache.pekko.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import org.apache.pekko.stream.scaladsl.{FileIO, Sink, _}
import org.apache.pekko.stream.Materializer
import org.apache.pekko.util.ByteString
import sttp.capabilities.pekko.PekkoStreams
import sttp.model.{Header, Part}
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interpreter.{RawValue, RequestBody}
import sttp.tapir.server.model.InvalidMultipartBodyException
import sttp.tapir.{FileRange, InputStreamRange, RawBodyType, RawPart}

import scala.concurrent.{ExecutionContext, Future}

private[pekkohttp] class PekkoRequestBody(serverOptions: PekkoHttpServerOptions)(implicit
    mat: Materializer,
    ec: ExecutionContext
) extends RequestBody[Future, PekkoStreams] {
  override val streams: PekkoStreams = PekkoStreams
  override def toRaw[R](request: ServerRequest, bodyType: RawBodyType[R], maxBytes: Option[Long]): Future[RawValue[R]] =
    toRawFromEntity(request, requestEntity(request, maxBytes), bodyType)

  override def toStream(request: ServerRequest, maxBytes: Option[Long]): streams.BinaryStream = {
    requestEntity(request, maxBytes).dataBytes
  }

  private def requestEntity(request: ServerRequest, maxBytes: Option[Long]): RequestEntity = {
    val entity = request.underlying.asInstanceOf[RequestContext].request.entity
    maxBytes.map(entity.withSizeLimit).getOrElse(entity)
  }

  private def toRawFromEntity[R](
      request: ServerRequest,
      body: HttpEntity,
      bodyType: RawBodyType[R]
  ): Future[RawValue[R]] = {
    bodyType match {
      case RawBodyType.StringBody(_)   => implicitly[FromEntityUnmarshaller[String]].apply(body).map(RawValue(_))
      case RawBodyType.ByteArrayBody   => implicitly[FromEntityUnmarshaller[Array[Byte]]].apply(body).map(RawValue(_))
      case RawBodyType.ByteBufferBody  => implicitly[FromEntityUnmarshaller[ByteString]].apply(body).map(b => RawValue(b.asByteBuffer))
      case RawBodyType.InputStreamBody =>
        Future.successful(RawValue(body.dataBytes.runWith(StreamConverters.asInputStream())))
      case RawBodyType.FileBody =>
        serverOptions
          .createFile(request)
          .flatMap(file =>
            body.dataBytes
              .runWith(FileIO.toPath(file.toPath))
              .recoverWith {
                // We need to dig out EntityStreamSizeException from an external wrapper applied by FileIO sink
                case e: Exception if e.getCause().isInstanceOf[EntityStreamSizeException] =>
                  Future.failed(e.getCause())
              }
              .map(_ => FileRange(file))
              .map(f => RawValue(f, Seq(f)))
          )
      case RawBodyType.InputStreamRangeBody =>
        Future.successful(RawValue(InputStreamRange(() => body.dataBytes.runWith(StreamConverters.asInputStream()))))
      case m: RawBodyType.MultipartBody =>
        implicitly[FromEntityUnmarshaller[Multipart.FormData]]
          .apply(body)
          .flatMap { fd =>
            fd.parts
              .mapConcat(part => m.partType(part.name).map((part, _)).toList)
              .mapAsync[RawPart](1) { case (part, codecMeta) => toRawPart(request, part, codecMeta) }
              .runWith[Future[scala.collection.immutable.Seq[RawPart]]](Sink.seq)
              .map(RawValue.fromParts)
              .asInstanceOf[Future[RawValue[R]]]
          }
          .recoverWith { case e: ParsingException => Future.failed(InvalidMultipartBodyException(e)) }
    }
  }

  private def toRawPart[R](request: ServerRequest, part: Multipart.FormData.BodyPart, bodyType: RawBodyType[R]): Future[Part[R]] = {
    toRawFromEntity(request, part.entity, bodyType)
      .map(r =>
        Part(
          part.name,
          r.value,
          otherDispositionParams = part.additionalDispositionParams,
          headers = part.additionalHeaders.map(h => Header(h.name, h.value))
        ).contentType(part.entity.contentType.toString())
      )
  }
}
