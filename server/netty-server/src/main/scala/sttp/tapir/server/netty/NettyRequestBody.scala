package sttp.tapir.server.netty

import java.nio.ByteBuffer
import java.nio.file.Files

import scala.concurrent.{ExecutionContext, Future}

import io.netty.handler.codec.http.FullHttpMessage
import sttp.capabilities
import sttp.tapir.RawBodyType
import sttp.tapir.server.interpreter.{RawValue, RequestBody}
import io.netty.buffer.{ByteBufInputStream, ByteBufUtil}
import sttp.tapir.internal.NoStreams
import sttp.tapir.model.ServerRequest

class NettyRequestBody(req: FullHttpMessage, serverRequest: ServerRequest, serverOptions: NettyServerOptions)(implicit
    val ec: ExecutionContext
) extends RequestBody[Future, NoStreams] {

  override val streams: capabilities.Streams[NoStreams] = NoStreams

  override def toRaw[RAW](bodyType: RawBodyType[RAW]): Future[RawValue[RAW]] = {
    bodyType match {
      case RawBodyType.StringBody(charset) => Future.successful(RawValue(req.content().toString(charset)))
      case RawBodyType.ByteArrayBody       => Future.successful(RawValue(requestContent))
      case RawBodyType.ByteBufferBody      => Future.successful(RawValue(ByteBuffer.wrap(requestContent)))
      case RawBodyType.InputStreamBody     => Future.successful(RawValue(new ByteBufInputStream(req.content())))
      case RawBodyType.FileBody =>
        serverOptions
          .createFile(serverRequest)
          .map(file => {
            Files.write(file.toPath, requestContent)
            RawValue(file, Seq(file))
          })
      case m: RawBodyType.MultipartBody => ???
    }
  }

  override def toStream(): streams.BinaryStream = throw new UnsupportedOperationException()

  /** [[ByteBufUtil.getBytes(io.netty.buffer.ByteBuf)]] copies buffer without affecting reader index of the original. */
  private def requestContent = ByteBufUtil.getBytes(req.content())
}
