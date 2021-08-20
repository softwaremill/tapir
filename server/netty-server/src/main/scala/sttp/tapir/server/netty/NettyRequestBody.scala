package sttp.tapir.server.netty

import scala.concurrent.Future

import io.netty.handler.codec.http.{FullHttpMessage, FullHttpRequest, HttpContent}
import sttp.capabilities
import sttp.tapir.RawBodyType
import sttp.tapir.server.interpreter.{RawValue, RequestBody}
import scala.concurrent.ExecutionContext.Implicits.global

import io.netty.buffer.ByteBufInputStream
import sttp.tapir.internal.NoStreams

//todo: ChannelFuture or something with cats-effect?
//todo: FullHttpMessage OK?
class NettyRequestBody(req: FullHttpMessage) extends RequestBody[Future, NoStreams] {

  override val streams: capabilities.Streams[NoStreams] = NoStreams

  override def toRaw[RAW](bodyType: RawBodyType[RAW]): Future[RawValue[RAW]] = {
    bodyType match {
      case RawBodyType.StringBody(charset) => Future(RawValue(req.content().toString(charset)))
      case RawBodyType.ByteArrayBody       => Future(RawValue(req.content().array()))
      case RawBodyType.ByteBufferBody      => Future(RawValue(req.content().nioBuffer()))
      case RawBodyType.InputStreamBody     => Future(RawValue(new ByteBufInputStream(req.content())))
      case RawBodyType.FileBody            => ???
      case RawBodyType.MultipartBody(_, _) => ???
    }
  }

  override def toStream(): streams.BinaryStream = throw new UnsupportedOperationException()
}
