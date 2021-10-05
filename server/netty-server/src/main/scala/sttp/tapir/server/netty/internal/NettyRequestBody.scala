package sttp.tapir.server.netty.internal

import io.netty.buffer.{ByteBufInputStream, ByteBufUtil}
import sttp.capabilities
import sttp.monad.MonadError
import sttp.tapir.{File, FileRange, RawBodyType}
import sttp.tapir.internal.NoStreams
import sttp.tapir.model.ServerRequest
import sttp.monad.syntax._
import sttp.tapir.server.interpreter.{RawValue, RequestBody}
import sttp.tapir.server.netty.NettyServerRequest

import java.nio.ByteBuffer
import java.nio.file.Files

class NettyRequestBody[F[_]](req: NettyServerRequest, serverRequest: ServerRequest, createFile: ServerRequest => F[File])(implicit
    monadError: MonadError[F]
) extends RequestBody[F, NoStreams] {

  override val streams: capabilities.Streams[NoStreams] = NoStreams

  override def toRaw[RAW](bodyType: RawBodyType[RAW]): F[RawValue[RAW]] = {
    bodyType match {
      case RawBodyType.StringBody(charset) => monadError.unit(RawValue(req.req.content().toString(charset)))
      case RawBodyType.ByteArrayBody       => monadError.unit(RawValue(requestContent))
      case RawBodyType.ByteBufferBody      => monadError.unit(RawValue(ByteBuffer.wrap(requestContent)))
      case RawBodyType.InputStreamBody     => monadError.unit(RawValue(new ByteBufInputStream(req.req.content())))
      case RawBodyType.FileBody =>
        createFile(serverRequest)
          .map(file => {
            Files.write(file.toPath, requestContent)
            RawValue(FileRange(file), Seq(FileRange(file)))
          })
      case _: RawBodyType.MultipartBody => ???
    }
  }

  override def toStream(): streams.BinaryStream = throw new UnsupportedOperationException()

  /** [[ByteBufUtil.getBytes(io.netty.buffer.ByteBuf)]] copies buffer without affecting reader index of the original. */
  private def requestContent = ByteBufUtil.getBytes(req.req.content())
}
