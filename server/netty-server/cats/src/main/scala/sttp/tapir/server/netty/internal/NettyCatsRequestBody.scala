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
import sttp.tapir.server.interpreter.{RawValue, RequestBody}
import sttp.tapir.{FileRange, InputStreamRange, RawBodyType, TapirFile}

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer

private[netty] class NettyCatsRequestBody[F[_]](createFile: ServerRequest => F[TapirFile])(implicit val monad: Async[F])
    extends RequestBody[F, Fs2Streams[F]] {

  override val streams: Fs2Streams[F] = Fs2Streams[F]

  override def toRaw[R](serverRequest: ServerRequest, bodyType: RawBodyType[R]): F[RawValue[R]] = {

    bodyType match {
      case RawBodyType.StringBody(charset) => nettyRequestBytes(serverRequest).map(bs => RawValue(new String(bs, charset)))
      case RawBodyType.ByteArrayBody =>
        nettyRequestBytes(serverRequest).map(RawValue(_))
      case RawBodyType.ByteBufferBody =>
        nettyRequestBytes(serverRequest).map(bs => RawValue(ByteBuffer.wrap(bs)))
      case RawBodyType.InputStreamBody =>
        nettyRequestBytes(serverRequest).map(bs => RawValue(new ByteArrayInputStream(bs)))
      case RawBodyType.InputStreamRangeBody =>
        nettyRequestBytes(serverRequest).map(bs => RawValue(InputStreamRange(() => new ByteArrayInputStream(bs))))
      case RawBodyType.FileBody =>
        createFile(serverRequest)
          .flatMap(tapirFile => {
            toStream(serverRequest)
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

  override def toStream(serverRequest: ServerRequest): streams.BinaryStream = {
    val nettyRequest = serverRequest.underlying.asInstanceOf[StreamedHttpRequest]
    fs2.Stream
      .eval(StreamSubscriber[F, HttpContent](NettyRequestBody.DefaultChunkSize))
      .flatMap(s => s.sub.stream(Sync[F].delay(nettyRequest.subscribe(s))))
      .flatMap(httpContent => fs2.Stream.chunk(Chunk.byteBuffer(httpContent.content.nioBuffer())))
  }

  private def nettyRequestBytes(serverRequest: ServerRequest): F[Array[Byte]] = serverRequest.underlying match {
    case req: FullHttpRequest   => monad.delay(ByteBufUtil.getBytes(req.content()))
    case _: StreamedHttpRequest => toStream(serverRequest).compile.to(Chunk).map(_.toArray[Byte])
    case other => monad.raiseError(new UnsupportedOperationException(s"Unexpected Netty request of type ${other.getClass().getName()}"))
  }
}
