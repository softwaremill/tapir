package sttp.tapir.server.netty

import scala.concurrent.Future

import io.netty.handler.codec.http.FullHttpRequest
import sttp.capabilities
import sttp.tapir.RawBodyType
import sttp.tapir.server.interpreter.{RawValue, RequestBody}
import scala.concurrent.ExecutionContext.Implicits.global

import io.netty.buffer.ByteBufInputStream
import sttp.tapir.internal.NoStreams

//todo: ChannelFuture or something with cats-effect?
//todo: FullHttpRequest vs multipart?
class NettyRequestBody(req: FullHttpRequest) extends RequestBody[Future, NoStreams] {

  override val streams: capabilities.Streams[NoStreams] = NoStreams

  override def toRaw[RAW](bodyType: RawBodyType[RAW]): Future[RawValue[RAW]] = {
    Future(req.content())
      .map { cnt =>
        bodyType match {
          case RawBodyType.StringBody(charset) => cnt.toString(charset)
          case RawBodyType.ByteArrayBody       => cnt.array()
          case RawBodyType.ByteBufferBody      => cnt.nioBuffer()
          case RawBodyType.InputStreamBody     => new ByteBufInputStream(cnt)
        }
      }
      .map(RawValue(_))

    bodyType match {
      case RawBodyType.StringBody(charset) => Future(req.content()).map(_.toString(charset)).map(RawValue(_))
      case RawBodyType.ByteArrayBody       => Future(req.content()).map(_.array()).map(RawValue(_))
      case RawBodyType.ByteBufferBody      => Future(req.content()).map(_.nioBuffer()).map(RawValue(_))
      case RawBodyType.InputStreamBody     => Future(req.content()).map(new ByteBufInputStream(_)).map(RawValue(_))
      case RawBodyType.FileBody            => ???
      case RawBodyType.MultipartBody(_, _) => ???
    }
  }

  override def toStream(): streams.BinaryStream = throw new UnsupportedOperationException()
}
