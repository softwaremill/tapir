package sttp.tapir.server.pekkohttp

import org.apache.pekko.http.scaladsl.model.{HttpEntity, Multipart}
import org.apache.pekko.http.scaladsl.server.RequestContext
import org.apache.pekko.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{FileIO, Sink}
import org.apache.pekko.util.ByteString
import sttp.capabilities.pekko.PekkoStreams
import sttp.model.{Header, Part}
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interpreter.{RawValue, RequestBody}
import sttp.tapir.{FileRange, RawBodyType, RawPart, InputStreamRange}

import java.io.ByteArrayInputStream

import scala.concurrent.{ExecutionContext, Future}
import org.apache.pekko.http.scaladsl.model.EntityStreamSizeException
import org.apache.pekko.stream.StreamLimitReachedException
import org.apache.pekko.stream.scaladsl._
import sttp.capabilities.StreamMaxLengthExceededException
import org.apache.pekko.http.scaladsl.model.RequestEntity
import org.apache.pekko.stream.IOOperationIncompleteException
import scala.util.Failure

private[pekkohttp] class PekkoRequestBody(serverOptions: PekkoHttpServerOptions)(implicit
    mat: Materializer,
    ec: ExecutionContext
) extends RequestBody[Future, PekkoStreams] {
  override val streams: PekkoStreams = PekkoStreams
  override def toRaw[R](request: ServerRequest, bodyType: RawBodyType[R], maxBytes: Option[Long]): Future[RawValue[R]] =
    toRawFromEntity(request, requestEntity(request, maxBytes), bodyType, maxBytes)

  override def toStream(request: ServerRequest, maxBytes: Option[Long]): streams.BinaryStream = {
    requestEntity(request, maxBytes).dataBytes.mapError { case EntityStreamSizeException(limit, _) =>
      new StreamMaxLengthExceededException(limit)
    }
  }

  private def requestEntity(request: ServerRequest, maxBytes: Option[Long]): RequestEntity = {
    val entity = request.underlying.asInstanceOf[RequestContext].request.entity
    maxBytes.map(entity.withSizeLimit).getOrElse(entity)
  }

  private def toRawFromEntity[R](
      request: ServerRequest,
      body: HttpEntity,
      bodyType: RawBodyType[R],
      maxBytes: Option[Long]
  ): Future[RawValue[R]] = {
    bodyType match {
      case RawBodyType.StringBody(_)  => implicitly[FromEntityUnmarshaller[String]].apply(body).map(RawValue(_))
      case RawBodyType.ByteArrayBody  => implicitly[FromEntityUnmarshaller[Array[Byte]]].apply(body).map(RawValue(_))
      case RawBodyType.ByteBufferBody => implicitly[FromEntityUnmarshaller[ByteString]].apply(body).map(b => RawValue(b.asByteBuffer))
      case RawBodyType.InputStreamBody =>
        Future.successful(RawValue(toStream(request, maxBytes).runWith(StreamConverters.asInputStream())))
      case RawBodyType.FileBody =>
        serverOptions
          .createFile(request)
          .flatMap(file =>
            toStream(request, maxBytes)
              .runWith(FileIO.toPath(file.toPath)).map(_ => FileRange(file)).map(f => RawValue(f, Seq(f)))
          )
          .recoverWith {
            case e: IOOperationIncompleteException if e.getCause().isInstanceOf[StreamMaxLengthExceededException] =>
              Future.failed(e.getCause())
          }
      case RawBodyType.InputStreamRangeBody =>
        Future.successful(RawValue(InputStreamRange(() => toStream(request, maxBytes).runWith(StreamConverters.asInputStream()))))
      case m: RawBodyType.MultipartBody =>
        implicitly[FromEntityUnmarshaller[Multipart.FormData]].apply(body).flatMap { fd =>
          fd.parts
            .mapConcat(part => m.partType(part.name).map((part, _)).toList)
            .mapAsync[RawPart](1) { case (part, codecMeta) => toRawPart(request, part, codecMeta) }
            .runWith[Future[scala.collection.immutable.Seq[RawPart]]](Sink.seq)
            .map(RawValue.fromParts)
            .asInstanceOf[Future[RawValue[R]]]
        }
    }
  }

  // Pekko Streams sinks like FileIO or StreamConverters.fromInputStream wrap our exception into their own
  // exceptions, so we need to "rethrow" with our exception again.
  private def unwrapStreamMaxLengthExceededException[T](operation: Future[T]): Future[T] =
    operation.recoverWith {
      // The FileIO sink wraps with their own exception, which we want to unwrap
      case e: RuntimeException if e.getCause().isInstanceOf[StreamMaxLengthExceededException] =>
        Future.failed(e.getCause())
    }

  private def toRawPart[R](request: ServerRequest, part: Multipart.FormData.BodyPart, bodyType: RawBodyType[R]): Future[Part[R]] = {
    toRawFromEntity(request, part.entity, bodyType, maxBytes = None)
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
