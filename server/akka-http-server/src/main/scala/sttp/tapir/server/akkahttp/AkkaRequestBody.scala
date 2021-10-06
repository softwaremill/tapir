package sttp.tapir.server.akkahttp

import akka.http.scaladsl.model.{HttpEntity, Multipart}
import akka.http.scaladsl.server.RequestContext
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import akka.stream.Materializer
import akka.stream.scaladsl.{FileIO, Sink}
import akka.util.ByteString
import sttp.capabilities.akka.AkkaStreams
import sttp.model.{Header, Part}
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interpreter.{RawValue, RequestBody}
import sttp.tapir.{FileRange, RawBodyType, RawPart}

import java.io.ByteArrayInputStream
import scala.concurrent.{ExecutionContext, Future}

private[akkahttp] class AkkaRequestBody(ctx: RequestContext, request: ServerRequest, serverOptions: AkkaHttpServerOptions)(implicit
    mat: Materializer,
    ec: ExecutionContext
) extends RequestBody[Future, AkkaStreams] {
  override val streams: AkkaStreams = AkkaStreams
  override def toRaw[R](bodyType: RawBodyType[R]): Future[RawValue[R]] = toRawFromEntity(ctx.request.entity, bodyType)
  override def toStream(): streams.BinaryStream = ctx.request.entity.dataBytes

  private def toRawFromEntity[R](body: HttpEntity, bodyType: RawBodyType[R]): Future[RawValue[R]] = {
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
      case m: RawBodyType.MultipartBody =>
        implicitly[FromEntityUnmarshaller[Multipart.FormData]].apply(body).flatMap { fd =>
          fd.parts
            .mapConcat(part => m.partType(part.name).map((part, _)).toList)
            .mapAsync[RawPart](1) { case (part, codecMeta) => toRawPart(part, codecMeta) }
            .runWith[Future[scala.collection.immutable.Seq[RawPart]]](Sink.seq)
            .map(RawValue.fromParts)
            .asInstanceOf[Future[RawValue[R]]]
        }
    }
  }

  private def toRawPart[R](part: Multipart.FormData.BodyPart, bodyType: RawBodyType[R]): Future[Part[R]] = {
    toRawFromEntity(part.entity, bodyType)
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
