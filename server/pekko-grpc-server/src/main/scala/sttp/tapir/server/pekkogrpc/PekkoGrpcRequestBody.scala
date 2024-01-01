package sttp.tapir.server.pekkogrpc

import org.apache.pekko.grpc.internal.{GrpcProtocolNative, Identity, SingleParameterSink}
import org.apache.pekko.http.scaladsl.model.HttpEntity
import org.apache.pekko.http.scaladsl.server.RequestContext
import org.apache.pekko.stream.Materializer
import org.apache.pekko.util.ByteString
import sttp.capabilities.pekko.PekkoStreams
import sttp.tapir.{InputStreamRange, RawBodyType}
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.pekkohttp.PekkoHttpServerOptions
import sttp.tapir.server.interpreter.{RawValue, RequestBody}

import java.io.ByteArrayInputStream
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

private[pekkogrpc] class PekkoGrpcRequestBody(serverOptions: PekkoHttpServerOptions)(implicit
    mat: Materializer,
    ec: ExecutionContext
) extends RequestBody[Future, PekkoStreams] {
  private val grpcProtocol = GrpcProtocolNative.newReader(Identity)

  override val streams: PekkoStreams = PekkoStreams
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
