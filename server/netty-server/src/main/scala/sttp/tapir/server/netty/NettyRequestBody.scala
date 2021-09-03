package sttp.tapir.server.netty

import java.nio.ByteBuffer

import scala.concurrent.{ExecutionContext, Future}

import io.netty.handler.codec.http.FullHttpMessage
import sttp.capabilities
import sttp.tapir.RawBodyType
import sttp.tapir.server.interpreter.{RawValue, RequestBody}
import io.netty.buffer.{ByteBufInputStream, ByteBufUtil}
import sttp.tapir.internal.NoStreams

class NettyRequestBody(req: FullHttpMessage)(implicit val ec: ExecutionContext) extends RequestBody[Future, NoStreams] {

  override val streams: capabilities.Streams[NoStreams] = NoStreams

  override def toRaw[RAW](bodyType: RawBodyType[RAW]): Future[RawValue[RAW]] = {
    bodyType match {
      case RawBodyType.StringBody(charset) => Future.successful(RawValue(new String(requestContent, charset)))
      case RawBodyType.ByteArrayBody       => Future.successful(RawValue(requestContent))
      case RawBodyType.ByteBufferBody      => Future.successful(RawValue(ByteBuffer.wrap(requestContent)))
      case RawBodyType.InputStreamBody     => Future.successful(RawValue(new ByteBufInputStream(req.content())))
      case RawBodyType.FileBody            => ???
      case RawBodyType.MultipartBody(_, _) => ???
    }
  }

  override def toStream(): streams.BinaryStream = throw new UnsupportedOperationException()

  /** [[ByteBufUtil.getBytes(io.netty.buffer.ByteBuf)]] copies buffer without affecting reader index of the original. */
  private def requestContent = ByteBufUtil.getBytes(req.content())
}
