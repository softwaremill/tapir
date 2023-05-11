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

import java.io.{ByteArrayInputStream, InputStream}

import scala.concurrent.{ExecutionContext, Future}

private[pekkohttp] class PekkoRequestBody(serverOptions: PekkoHttpServerOptions)(implicit
    mat: Materializer,
    ec: ExecutionContext
) extends RequestBody[Future, PekkoStreams] {
  override val streams: PekkoStreams = PekkoStreams
  override def toRaw[R](request: ServerRequest, bodyType: RawBodyType[R]): Future[RawValue[R]] =
    toRawFromEntity(request, akkeRequestEntity(request), bodyType)
  override def toStream(request: ServerRequest): streams.BinaryStream = akkeRequestEntity(request).dataBytes

  private def akkeRequestEntity(request: ServerRequest) = request.underlying.asInstanceOf[RequestContext].request.entity

  private def toRawFromEntity[R](request: ServerRequest, body: HttpEntity, bodyType: RawBodyType[R]): Future[RawValue[R]] = {
    bodyType match {
      case RawBodyType.StringBody(_)  => implicitly[FromEntityUnmarshaller[String]].apply(body).map(RawValue(_))
      case RawBodyType.ByteArrayBody  => implicitly[FromEntityUnmarshaller[Array[Byte]]].apply(body).map(RawValue(_))
      case RawBodyType.ByteBufferBody => implicitly[FromEntityUnmarshaller[ByteString]].apply(body).map(b => RawValue(b.asByteBuffer))
      case RawBodyType.InputStreamBody =>
        implicitly[FromEntityUnmarshaller[Array[Byte]]].apply(body).map(b => RawValue(new ByteArrayInputStream(b)))
      case RawBodyType.FileBody =>
        serverOptions
          .createFile(request)
          .flatMap(file => body.dataBytes.runWith(FileIO.toPath(file.toPath)).map(_ => FileRange(file)).map(f => RawValue(f, Seq(f))))
      case RawBodyType.InputStreamRangeBody =>
        implicitly[FromEntityUnmarshaller[Array[Byte]]]
          .apply(body)
          .map(b => RawValue(InputStreamRange(() => new ByteArrayInputStream(b))))
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
