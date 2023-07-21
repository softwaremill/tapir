package sttp.tapir.server.netty.zio.internal

import _root_.zio._
import _root_.zio.interop.reactivestreams._
import _root_.zio.stream.Stream
import org.reactivestreams.Publisher
import sttp.tapir.server.netty._
import sttp.tapir.server.netty.internal._
import sttp.capabilities.zio.ZioStreams
import io.netty.handler.codec.http.DefaultHttpContent
import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.HttpContent
import sttp.tapir.FileRange
import _root_.zio.stream.ZStream
import java.io.InputStream

private[zio] object ZioStreamCompatible {

  def apply(runtime: Runtime[Any]): StreamCompatible[ZioStreams] = {
    new StreamCompatible[ZioStreams] {
      override val streams: ZioStreams = ZioStreams

      override def fromFile(fileRange: FileRange): streams.BinaryStream = {
        fileRange.range
          .flatMap(r => r.startAndEnd.map { case(fStart, _) => 
            ZStream.fromPath(fileRange.file.toPath)
            .drop(fStart.toInt)
            .take(r.contentLength)
          })
          .getOrElse(
            ZStream.fromPath(fileRange.file.toPath)
            )
      }

      override def fromInputStream(is: () => InputStream, length: Option[Long]): streams.BinaryStream = 
        length match {
          case Some(limitedLength) => ZStream.fromInputStream(is()).take(limitedLength.toInt)
          case None => ZStream.fromInputStream(is())
      }

      override def asPublisher(stream: Stream[Throwable, Byte]): Publisher[HttpContent] =
        Unsafe.unsafe(implicit u =>
          runtime.unsafe
            .run(stream.mapChunks(c => Chunk.single(DefaultHttpContent(Unpooled.wrappedBuffer(c.toArray)))).toPublisher)
            .getOrThrowFiberFailure()
        )

      override def fromNettyStream(publisher: Publisher[HttpContent]): Stream[Throwable, Byte] =
        publisher.toZIOStream().mapConcatChunk(httpContent => Chunk.fromByteBuffer(httpContent.content.nioBuffer()))
    }
  }
}
