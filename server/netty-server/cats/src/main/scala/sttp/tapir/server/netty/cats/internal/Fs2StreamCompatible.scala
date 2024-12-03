package sttp.tapir.server.netty.cats.internal

import cats.effect.kernel.{Async, Sync}
import cats.effect.std.Dispatcher
import fs2.interop.reactivestreams.{StreamSubscriber, StreamUnicastPublisher}
import fs2.io.file.{Files, Flags, Path}
import fs2.{Chunk, Pipe}
import io.netty.buffer.Unpooled
import io.netty.channel.{ChannelFuture, ChannelHandlerContext}
import io.netty.handler.codec.http.websocketx._
import io.netty.handler.codec.http.{DefaultHttpContent, HttpContent}
import org.reactivestreams.{Processor, Publisher}
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.server.netty.internal._
import sttp.tapir.{FileRange, WebSocketBodyOutput}

import java.io.InputStream

object Fs2StreamCompatible {

  private[cats] def apply[F[_]: Async](dispatcher: Dispatcher[F]): StreamCompatible[Fs2Streams[F]] = {
    new StreamCompatible[Fs2Streams[F]] {
      override val streams: Fs2Streams[F] = Fs2Streams[F]

      override def fromFile(fileRange: FileRange, chunkSize: Int): streams.BinaryStream = {
        val path = Path.fromNioPath(fileRange.file.toPath)
        fileRange.range
          .flatMap(r => r.startAndEnd.map(s => Files[F](Files.forAsync[F]).readRange(path, chunkSize, s._1, s._2)))
          .getOrElse(Files[F](Files.forAsync[F]).readAll(path, chunkSize, Flags.Read))
      }

      override def fromInputStream(is: () => InputStream, chunkSize: Int, length: Option[Long]): streams.BinaryStream =
        length match {
          case Some(limitedLength) => inputStreamToFs2(is, chunkSize).take(limitedLength)
          case None                => inputStreamToFs2(is, chunkSize)
        }

      override def asPublisher(stream: fs2.Stream[F, Byte]): Publisher[HttpContent] =
        // Deprecated constructor, but the proposed one does roughly the same, forcing a dedicated
        // dispatcher, which results in a Resource[], which is hard to afford here
        StreamUnicastPublisher(
          stream.mapChunks { chunk =>
            val bytes: Chunk.ArraySlice[Byte] = chunk.compact
            Chunk.singleton(new DefaultHttpContent(Unpooled.wrappedBuffer(bytes.values, bytes.offset, bytes.length)))
          },
          dispatcher
        )

      override def fromPublisher(publisher: Publisher[HttpContent], maxBytes: Option[Long]): streams.BinaryStream = {
        val stream = fs2.Stream
          .eval(StreamSubscriber[F, HttpContent](bufferSize = 2))
          .flatMap(s => s.sub.stream(Sync[F].delay(publisher.subscribe(s))))
          .flatMap(httpContent =>
            fs2.Stream.chunk {
              // #4194: we need to copy the data here as we don't know when the data will be ultimately read, and hence
              // when we'll be able to release the Netty buffer
              val buf = httpContent.content.nioBuffer()
              try {
                val content = new Array[Byte](buf.remaining())
                buf.get(content)
                Chunk.array(content)
              } finally { val _ = httpContent.release() } // https://netty.io/wiki/reference-counted-objects.html
            }
          )
        maxBytes.map(Fs2Streams.limitBytes(stream, _)).getOrElse(stream)
      }

      override def failedStream(e: => Throwable): streams.BinaryStream =
        fs2.Stream.raiseError(e)

      override def emptyStream: streams.BinaryStream =
        fs2.Stream.empty

      override def asWsProcessor[REQ, RESP](
          pipe: Pipe[F, REQ, RESP],
          o: WebSocketBodyOutput[Pipe[F, REQ, RESP], REQ, RESP, ?, Fs2Streams[F]],
          ctx: ChannelHandlerContext
      ): Processor[WebSocketFrame, WebSocketFrame] = {
        val wsCompletedPromise = ctx.newPromise()
        wsCompletedPromise.addListener((f: ChannelFuture) => {
          // A special callback that has to be used when a SteramSubscription cancels or fails.
          // This can happen in case of errors in the pipeline which are not signalled correctly,
          // like throwing exceptions directly.
          // Without explicit Close frame a client may hang on waiting and not knowing about closed channel.
          if (f.isCancelled) {
            val _ = ctx.writeAndFlush(new CloseWebSocketFrame(WebSocketCloseStatus.NORMAL_CLOSURE, "Canceled"))
          } else if (!f.isSuccess) {
            val _ = ctx.writeAndFlush(new CloseWebSocketFrame(WebSocketCloseStatus.INTERNAL_SERVER_ERROR, "Error"))
          }
        })
        new WebSocketPipeProcessor[F, REQ, RESP](pipe, dispatcher, o, wsCompletedPromise)
      }

      private def inputStreamToFs2(inputStream: () => InputStream, chunkSize: Int) =
        fs2.io.readInputStream(
          Sync[F].blocking(inputStream()),
          chunkSize
        )
    }
  }
}
