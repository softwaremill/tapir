package sttp.tapir.server.netty.internal

import cats.effect.{Async, Sync}
import cats.syntax.all._
import io.netty.buffer.{ByteBufInputStream, ByteBufUtil}
import io.netty.handler.codec.http.FullHttpRequest
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.{FileRange, InputStreamRange, RawBodyType, TapirFile}
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interpreter.{RawValue, RequestBody}

import java.nio.ByteBuffer
import java.nio.file.Files

private[netty] class NettyCatsRequestBody[F[_]](createFile: ServerRequest => F[TapirFile])(implicit val monad: Async[F])
    extends RequestBody[F, Fs2Streams[F]] {

  val streamChunkSize = 8192
  override val streams: Fs2Streams[F] = Fs2Streams[F]

  override def toRaw[R](serverRequest: ServerRequest, bodyType: RawBodyType[R]): F[RawValue[R]] = {

    /** [[ByteBufUtil.getBytes(io.netty.buffer.ByteBuf)]] copies buffer without affecting reader index of the original. */
    def requestContentAsByteArray = ByteBufUtil.getBytes(nettyRequest(serverRequest).content())

    bodyType match {
      case RawBodyType.StringBody(charset) => monad.delay(RawValue(nettyRequest(serverRequest).content().toString(charset)))
      case RawBodyType.ByteArrayBody       => monad.delay(RawValue(requestContentAsByteArray))
      case RawBodyType.ByteBufferBody      => monad.delay(RawValue(ByteBuffer.wrap(requestContentAsByteArray)))
      case RawBodyType.InputStreamBody     => monad.delay(RawValue(new ByteBufInputStream(nettyRequest(serverRequest).content())))
      case RawBodyType.InputStreamRangeBody =>
        monad.delay(RawValue(InputStreamRange(() => new ByteBufInputStream(nettyRequest(serverRequest).content()))))
      case RawBodyType.FileBody =>
        createFile(serverRequest)
          .map(file => {
            Files.write(file.toPath, requestContentAsByteArray)
            RawValue(FileRange(file), Seq(FileRange(file)))
          })
      case _: RawBodyType.MultipartBody => ???
    }
  }

  override def toStream(serverRequest: ServerRequest): streams.BinaryStream = {
    fs2.io.readInputStream(Sync[F].delay(new ByteBufInputStream(nettyRequest(serverRequest).content())), streamChunkSize)
  }

  private def nettyRequest(serverRequest: ServerRequest): FullHttpRequest = serverRequest.underlying.asInstanceOf[FullHttpRequest]
}
