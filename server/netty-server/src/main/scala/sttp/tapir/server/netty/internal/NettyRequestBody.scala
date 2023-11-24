package sttp.tapir.server.netty.internal

import io.netty.buffer.{ByteBufInputStream, ByteBufUtil}
import io.netty.handler.codec.http.FullHttpRequest
import sttp.capabilities
import sttp.monad.MonadError
import sttp.tapir.{FileRange, InputStreamRange, RawBodyType, TapirFile}
import sttp.tapir.model.ServerRequest
import sttp.monad.syntax._
import sttp.tapir.capabilities.NoStreams
import sttp.tapir.server.interpreter.{RawValue, RequestBody}

import java.nio.ByteBuffer
import java.nio.file.Files
import io.netty.buffer.ByteBuf
import sttp.tapir.DecodeResult
import sttp.capabilities.StreamMaxLengthExceededException

class NettyRequestBody[F[_]](createFile: ServerRequest => F[TapirFile])(implicit
    monadError: MonadError[F]
) extends RequestBody[F, NoStreams] {

  override val streams: capabilities.Streams[NoStreams] = NoStreams

  override def toRaw[RAW](serverRequest: ServerRequest, bodyType: RawBodyType[RAW], maxBytes: Option[Long]): F[RawValue[RAW]] = {

    def byteBuf: F[ByteBuf] = {
      val buf = nettyRequest(serverRequest).content()
      maxBytes
        .map(max =>
          if (buf.readableBytes() > max)
            monadError.error[ByteBuf](StreamMaxLengthExceededException(max))
          else
            monadError.unit(buf)
        )
        .getOrElse(monadError.unit(buf))
    }

    /** [[ByteBufUtil.getBytes(io.netty.buffer.ByteBuf)]] copies buffer without affecting reader index of the original. */
    def requestContentAsByteArray: F[Array[Byte]] = byteBuf.map(ByteBufUtil.getBytes)

    bodyType match {
      case RawBodyType.StringBody(charset) => byteBuf.map(buf => RawValue(buf.toString(charset)))
      case RawBodyType.ByteArrayBody       => requestContentAsByteArray.map(ba => RawValue(ba))
      case RawBodyType.ByteBufferBody      => requestContentAsByteArray.map(ba => RawValue(ByteBuffer.wrap(ba)))
      case RawBodyType.InputStreamBody     => byteBuf.map(buf => RawValue(new ByteBufInputStream(buf)))
      case RawBodyType.InputStreamRangeBody =>
        byteBuf.map(buf => RawValue(InputStreamRange(() => new ByteBufInputStream(buf))))
      case RawBodyType.FileBody =>
        requestContentAsByteArray.flatMap(ba =>
          createFile(serverRequest)
            .map(file => {
              Files.write(file.toPath, ba)
              RawValue(FileRange(file), Seq(FileRange(file)))
            })
        )
      case _: RawBodyType.MultipartBody => ???
    }
  }

  override def toStream(serverRequest: ServerRequest, maxBytes: Option[Long]): streams.BinaryStream =
    throw new UnsupportedOperationException()

  private def nettyRequest(serverRequest: ServerRequest): FullHttpRequest = serverRequest.underlying.asInstanceOf[FullHttpRequest]
}

private[internal] object NettyRequestBody {
  val DefaultChunkSize = 8192
}
