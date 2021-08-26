package sttp.tapir.server.netty

import scala.concurrent.{ExecutionContext, Future}

import io.netty.handler.codec.http.FullHttpMessage
import sttp.capabilities
import sttp.tapir.RawBodyType
import sttp.tapir.server.interpreter.{RawValue, RequestBody}
import io.netty.buffer.ByteBufInputStream
import sttp.tapir.internal.NoStreams

class NettyRequestBody(req: FullHttpMessage)(implicit val ec: ExecutionContext) extends RequestBody[Future, NoStreams] {

  override val streams: capabilities.Streams[NoStreams] = NoStreams

  override def toRaw[RAW](bodyType: RawBodyType[RAW]): Future[RawValue[RAW]] = {
    bodyType match {
      case RawBodyType.StringBody(charset) => Future.successful(RawValue(req.content().toString(charset)))
      case RawBodyType.ByteArrayBody       => Future.successful(RawValue(req.content().array()))
      case RawBodyType.ByteBufferBody      => Future.successful(RawValue(req.content().nioBuffer()))
      case RawBodyType.InputStreamBody     => Future.successful(RawValue(new ByteBufInputStream(req.content())))
      case RawBodyType.FileBody            => ???
      case RawBodyType.MultipartBody(_, _) => ???
    }
  }

  override def toStream(): streams.BinaryStream = throw new UnsupportedOperationException()
}
