package sttp.tapir.server.netty.internal

import cats.effect.{Async, Sync}
import cats.syntax.all._
import org.playframework.netty.http.StreamedHttpRequest
import fs2.Chunk
import fs2.interop.reactivestreams.StreamSubscriber
import fs2.io.file.{Files, Path}
import io.netty.buffer.ByteBufUtil
import io.netty.handler.codec.http.{FullHttpRequest, HttpContent}
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interpreter.{RawValue, RequestBody, RequestBodyToRawException}
import sttp.tapir.{FileRange, InputStreamRange, RawBodyType, TapirFile}

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import sttp.tapir.DecodeResult

private[netty] class NettyCatsRequestBody[F[_]](createFile: ServerRequest => F[TapirFile])(implicit val monad: Async[F])
    extends RequestBody[F, Fs2Streams[F]] {

  override val streams: Fs2Streams[F] = Fs2Streams[F]

  override def toRaw[R](serverRequest: ServerRequest, bodyType: RawBodyType[R], maxBytes: Option[Long]): F[RawValue[R]] = {

    def nettyRequestBytes: F[Array[Byte]] = serverRequest.underlying match {
      case req: FullHttpRequest =>
        val buf = req.content()
        maxBytes
          .map(max =>
            if (buf.readableBytes() > max)
              monad.raiseError[Array[Byte]](RequestBodyToRawException(DecodeResult.BodyTooLarge(max)))
            else
              monad.delay(ByteBufUtil.getBytes(buf))
          )
          .getOrElse(monad.delay(ByteBufUtil.getBytes(buf)))
      case _: StreamedHttpRequest => toStream(serverRequest, maxBytes).compile.to(Chunk).map(_.toArray[Byte])
      case other => monad.raiseError(new UnsupportedOperationException(s"Unexpected Netty request of type ${other.getClass().getName()}"))
    }
    bodyType match {
      case RawBodyType.StringBody(charset) => nettyRequestBytes.map(bs => RawValue(new String(bs, charset)))
      case RawBodyType.ByteArrayBody =>
        nettyRequestBytes.map(RawValue(_))
      case RawBodyType.ByteBufferBody =>
        nettyRequestBytes.map(bs => RawValue(ByteBuffer.wrap(bs)))
      case RawBodyType.InputStreamBody =>
        nettyRequestBytes.map(bs => RawValue(new ByteArrayInputStream(bs)))
      case RawBodyType.InputStreamRangeBody =>
        nettyRequestBytes.map(bs => RawValue(InputStreamRange(() => new ByteArrayInputStream(bs))))
      case RawBodyType.FileBody =>
        createFile(serverRequest)
          .flatMap(tapirFile => {
            toStream(serverRequest, maxBytes)
              .through(
                Files[F](Files.forAsync[F]).writeAll(Path.fromNioPath(tapirFile.toPath))
              )
              .compile
              .drain
              .map(_ => RawValue(FileRange(tapirFile), Seq(FileRange(tapirFile))))
          })
      case _: RawBodyType.MultipartBody => ???
    }
  }

  override def toStream(serverRequest: ServerRequest, maxBytes: Option[Long]): streams.BinaryStream = {
    val nettyRequest = serverRequest.underlying.asInstanceOf[StreamedHttpRequest]
    val stream = fs2.Stream
      .eval(StreamSubscriber[F, HttpContent](NettyRequestBody.DefaultChunkSize))
      .flatMap(s => s.sub.stream(Sync[F].delay(nettyRequest.subscribe(s))))
      .flatMap(httpContent => fs2.Stream.chunk(Chunk.byteBuffer(httpContent.content.nioBuffer())))
    maxBytes.map(Fs2Streams.limitBytes(stream, _)).getOrElse(stream)
  }
}
