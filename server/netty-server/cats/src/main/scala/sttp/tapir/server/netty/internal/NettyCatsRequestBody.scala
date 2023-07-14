package sttp.tapir.server.netty.internal

import cats.effect.{Async, Sync}
import cats.syntax.all._
import io.netty.buffer.{ByteBufInputStream, ByteBufUtil}
import io.netty.handler.codec.http.FullHttpRequest
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.{FileRange, InputStreamRange, RawBodyType, TapirFile}
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interpreter.{RawValue, RequestBody}
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import com.typesafe.netty.http.StreamedHttpRequest
import com.typesafe.netty.http.DefaultStreamedHttpRequest
import fs2.interop.reactivestreams.StreamSubscriber
import io.netty.handler.codec.http.HttpContent
import fs2.Chunk
import fs2.io.file.Files
import fs2.io.file.Path

private[netty] class NettyCatsRequestBody[F[_]](createFile: ServerRequest => F[TapirFile])(implicit val monad: Async[F])
    extends RequestBody[F, Fs2Streams[F]] {

  val streamChunkSize = 8192
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
      .eval(StreamSubscriber[F, HttpContent](NettyRequestBody.bufferSize))
      .flatMap(s => s.sub.stream(Sync[F].delay(nettyRequest.subscribe(s))))
      .flatMap(httpContent => fs2.Stream.chunk(Chunk.byteBuffer(httpContent.content.nioBuffer())))

    // fs2.io.readInputStream(Sync[F].delay(new ByteBufInputStream(nettyRequest(serverRequest).content())), streamChunkSize)
  }

  private def nettyRequestBytes(serverRequest: ServerRequest): F[Array[Byte]] = serverRequest.underlying match {
    case req: FullHttpRequest     => monad.delay(ByteBufUtil.getBytes(req.content()))
    case req: StreamedHttpRequest => toStream(serverRequest).compile.to(Chunk).map(_.toArray[Byte])
    case other => monad.raiseError(new UnsupportedOperationException(s"Unexpected Netty request of type ${other.getClass().getName()}"))
  }
}
