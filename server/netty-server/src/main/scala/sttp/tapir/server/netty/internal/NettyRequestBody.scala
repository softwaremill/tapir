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

class NettyRequestBody[F[_]](createFile: ServerRequest => F[TapirFile])(implicit
    monadError: MonadError[F]
) extends RequestBody[F, NoStreams] {

  override val streams: capabilities.Streams[NoStreams] = NoStreams

  override def toRaw[RAW](serverRequest: ServerRequest, bodyType: RawBodyType[RAW]): F[RawValue[RAW]] = {

    /** [[ByteBufUtil.getBytes(io.netty.buffer.ByteBuf)]] copies buffer without affecting reader index of the original. */
    def requestContentAsByteArray = ByteBufUtil.getBytes(nettyRequest(serverRequest).content())

    bodyType match {
      case RawBodyType.StringBody(charset) => monadError.unit(RawValue(nettyRequest(serverRequest).content().toString(charset)))
      case RawBodyType.ByteArrayBody       => monadError.unit(RawValue(requestContentAsByteArray))
      case RawBodyType.ByteBufferBody      => monadError.unit(RawValue(ByteBuffer.wrap(requestContentAsByteArray)))
      case RawBodyType.InputStreamBody     => monadError.unit(RawValue(new ByteBufInputStream(nettyRequest(serverRequest).content())))
      case RawBodyType.InputStreamRangeBody =>
        monadError.unit(RawValue(InputStreamRange(() => new ByteBufInputStream(nettyRequest(serverRequest).content()))))
      case RawBodyType.FileBody =>
        createFile(serverRequest)
          .map(file => {
            Files.write(file.toPath, requestContentAsByteArray)
            RawValue(FileRange(file), Seq(FileRange(file)))
          })
      case _: RawBodyType.MultipartBody => ???
    }
  }

  override def toStream(serverRequest: ServerRequest): streams.BinaryStream = throw new UnsupportedOperationException()

  private def nettyRequest(serverRequest: ServerRequest): FullHttpRequest = serverRequest.underlying.asInstanceOf[FullHttpRequest]
}

object NettyRequestBody {
  private[internal] val bufferSize = 8192
}
