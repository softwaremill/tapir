package sttp.tapir.server.akkagrpc

import akka.grpc.internal.{GrpcProtocolNative, Identity, SingleParameterSink}
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.server.RequestContext
import akka.stream.Materializer
import akka.util.ByteString
import sttp.capabilities.akka.AkkaStreams
import sttp.tapir.{InputStreamRange, RawBodyType}
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.akkahttp.AkkaHttpServerOptions
import sttp.tapir.server.interpreter.{RawValue, RequestBody}

import java.io.ByteArrayInputStream
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

private[akkagrpc] class AkkaGrpcRequestBody(serverOptions: AkkaHttpServerOptions)(implicit
    mat: Materializer,
    ec: ExecutionContext
) extends RequestBody[Future, AkkaStreams] {
  private val grpcProtocol = GrpcProtocolNative.newReader(Identity)

  override val streams: AkkaStreams = AkkaStreams
  override def toRaw[R](request: ServerRequest, bodyType: RawBodyType[R], maxBytes: Option[Long]): Future[RawValue[R]] =
    toRawFromEntity(request, akkaRequestEntity(request), bodyType)

  override def toStream(request: ServerRequest, maxBytes: Option[Long]): streams.BinaryStream = ???

  private def akkaRequestEntity(request: ServerRequest) = request.underlying.asInstanceOf[RequestContext].request.entity

  private def toRawFromEntity[R](request: ServerRequest, body: HttpEntity, bodyType: RawBodyType[R]): Future[RawValue[R]] = {
    // Copy-paste from akka.grpc.scaladsl.GrpcMarshalling#unmarshal
    body match {
      case HttpEntity.Strict(_, data) => Future.fromTry(Try(toExpectedBodyType(data, bodyType)))
      case _ => body.dataBytes.via(grpcProtocol.dataFrameDecoder).map(toExpectedBodyType(_, bodyType)).runWith(SingleParameterSink())
    }
  }

  private def toExpectedBodyType[R](byteString: ByteString, bodyType: RawBodyType[R]): RawValue[R] = {
    bodyType match {
      case RawBodyType.ByteArrayBody        => RawValue(byteString.toArray)
      case RawBodyType.ByteBufferBody       => RawValue(byteString.asByteBuffer)
      case RawBodyType.InputStreamBody      => RawValue(new ByteArrayInputStream(byteString.toArray))
      case RawBodyType.InputStreamRangeBody => RawValue(InputStreamRange(() => new ByteArrayInputStream(byteString.toArray)))
      case RawBodyType.FileBody             => ???
      case m: RawBodyType.MultipartBody     => ???
      case _                                => ???
    }
  }

}
