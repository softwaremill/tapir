package sttp.tapir.server.netty.internal

import cats.effect.kernel.{Async, Sync}
import cats.effect.std.Dispatcher
import fs2.Chunk
import fs2.interop.reactivestreams._
import fs2.io.file.{Files, Flags, Path}
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.{DefaultHttpContent, HttpContent}
import org.reactivestreams.Publisher
import sttp.capabilities.fs2.Fs2Streams
import sttp.model.HasHeaders
import sttp.tapir.server.interpreter.ToResponseBody
import sttp.tapir.server.netty.NettyResponse
import sttp.tapir.server.netty.NettyResponseContent._
import sttp.tapir.{CodecFormat, RawBodyType, WebSocketBodyOutput}

import java.io.InputStream
import java.nio.charset.Charset

class NettyCatsToResponseBody[F[_]: Async](dispatcher: Dispatcher[F], delegate: NettyToResponseBody)
    extends ToResponseBody[NettyResponse, Fs2Streams[F]] {
  override val streams: Fs2Streams[F] = Fs2Streams[F]

  override def fromRawValue[R](v: R, headers: HasHeaders, format: CodecFormat, bodyType: RawBodyType[R]): NettyResponse = {
    bodyType match {

      case RawBodyType.InputStreamBody =>
        val stream = inputStreamToFs2(() => v)
        (ctx: ChannelHandlerContext) => new ReactivePublisherNettyResponseContent(ctx.newPromise(), fs2StreamToPublisher(stream))

      case RawBodyType.InputStreamRangeBody =>
        val stream = v.range
          .map(range => inputStreamToFs2(v.inputStreamFromRangeStart).take(range.contentLength))
          .getOrElse(inputStreamToFs2(v.inputStream))
        (ctx: ChannelHandlerContext) => new ReactivePublisherNettyResponseContent(ctx.newPromise(), fs2StreamToPublisher(stream))

      case RawBodyType.FileBody =>
        val tapirFile = v
        val path = Path.fromNioPath(tapirFile.file.toPath)
        val stream = tapirFile.range
          .flatMap(r =>
            r.startAndEnd.map(s => Files[F](Files.forAsync[F]).readRange(path, NettyToResponseBody.DefaultChunkSize, s._1, s._2))
          )
          .getOrElse(Files[F](Files.forAsync[F]).readAll(path, NettyToResponseBody.DefaultChunkSize, Flags.Read))

        (ctx: ChannelHandlerContext) => new ReactivePublisherNettyResponseContent(ctx.newPromise(), fs2StreamToPublisher(stream))

      case _: RawBodyType.MultipartBody => throw new UnsupportedOperationException

      case _ => delegate.fromRawValue(v, headers, format, bodyType)
    }
  }

  private def inputStreamToFs2(inputStream: () => InputStream) =
    fs2.io.readInputStream(
      Sync[F].blocking(inputStream()),
      NettyToResponseBody.DefaultChunkSize
    )

  private def fs2StreamToPublisher(stream: streams.BinaryStream): Publisher[HttpContent] = {
    // Deprecated constructor, but the proposed one does roughly the same, forcing a dedicated
    // dispatcher, which results in a Resource[], which is hard to afford here
    StreamUnicastPublisher(
      stream
        .chunkLimit(NettyToResponseBody.DefaultChunkSize)
        .map { chunk =>
          val bytes: Chunk.ArraySlice[Byte] = chunk.compact

          new DefaultHttpContent(Unpooled.wrappedBuffer(bytes.values, bytes.offset, bytes.length))
        },
      dispatcher
    )
  }

  override def fromStreamValue(
      v: streams.BinaryStream,
      headers: HasHeaders,
      format: CodecFormat,
      charset: Option[Charset]
  ): NettyResponse =
    (ctx: ChannelHandlerContext) => {
      new ReactivePublisherNettyResponseContent(ctx.newPromise(), fs2StreamToPublisher(v))
    }

  override def fromWebSocketPipe[REQ, RESP](
      pipe: streams.Pipe[REQ, RESP],
      o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, Fs2Streams[F]]
  ): NettyResponse = throw new UnsupportedOperationException
}
