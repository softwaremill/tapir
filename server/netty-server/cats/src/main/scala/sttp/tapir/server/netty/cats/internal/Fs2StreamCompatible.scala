package sttp.tapir.server.netty.cats.internal

import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.{DefaultHttpContent, HttpContent}
import org.reactivestreams.Publisher
import sttp.tapir.FileRange
import sttp.tapir.server.netty.internal._

import java.io.InputStream
import cats.effect.std.Dispatcher
import sttp.capabilities.fs2.Fs2Streams
import fs2.io.file.Path
import fs2.io.file.Files
import cats.effect.kernel.Async
import fs2.io.file.Flags
import fs2.interop.reactivestreams.StreamUnicastPublisher
import cats.effect.kernel.Sync
import fs2.Chunk
import fs2.interop.reactivestreams.StreamSubscriber

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
              val fs2Chunk = Chunk.byteBuffer(httpContent.content.nioBuffer())
              httpContent.release() // https://netty.io/wiki/reference-counted-oubjects.html
              fs2Chunk
            }
          )
        maxBytes.map(Fs2Streams.limitBytes(stream, _)).getOrElse(stream)
      }

      override def failedStream(e: => Throwable): streams.BinaryStream =
        fs2.Stream.raiseError(e)

      override def emptyStream: streams.BinaryStream =
        fs2.Stream.empty

      private def inputStreamToFs2(inputStream: () => InputStream, chunkSize: Int) =
        fs2.io.readInputStream(
          Sync[F].blocking(inputStream()),
          chunkSize
        )
    }
  }
}
