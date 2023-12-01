package sttp.tapir.server.netty.zio.internal

import _root_.zio._
import _root_.zio.interop.reactivestreams._
import _root_.zio.stream.{Stream, ZStream}
import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.{DefaultHttpContent, HttpContent}
import org.reactivestreams.Publisher
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.FileRange
import sttp.tapir.server.netty.internal._

import java.io.InputStream

private[zio] object ZioStreamCompatible {

  def apply(runtime: Runtime[Any]): StreamCompatible[ZioStreams] = {
    new StreamCompatible[ZioStreams] {
      override val streams: ZioStreams = ZioStreams

      override def fromFile(fileRange: FileRange, chunkSize: Int): streams.BinaryStream = {
        fileRange.range
          .flatMap(r =>
            r.startAndEnd.map { case (fStart, _) =>
              ZStream
                .fromPath(fileRange.file.toPath, chunkSize)
                .drop(fStart.toInt)
                .take(r.contentLength)
            }
          )
          .getOrElse(
            ZStream.fromPath(fileRange.file.toPath)
          )
      }

      override def fromInputStream(is: () => InputStream, chunkSize: Int, length: Option[Long]): streams.BinaryStream =
        length match {
          case Some(limitedLength) => ZStream.fromInputStream(is(), chunkSize).take(limitedLength.toInt)
          case None                => ZStream.fromInputStream(is(), chunkSize)
        }

      override def asPublisher(stream: Stream[Throwable, Byte]): Publisher[HttpContent] =
        Unsafe.unsafe(implicit u =>
          runtime.unsafe
            .run(stream.mapChunks(c => Chunk.single(new DefaultHttpContent(Unpooled.wrappedBuffer(c.toArray)): HttpContent)).toPublisher)
            .getOrThrowFiberFailure()
        )

      override def fromPublisher(publisher: Publisher[HttpContent], maxBytes: Option[Long]): streams.BinaryStream = {
        val stream =
          Adapters
            .publisherToStream(publisher, bufferSize = 2)
            .map(httpContent => Chunk.fromByteBuffer(httpContent.content.nioBuffer()))
            .flattenChunks
        maxBytes.map(ZioStreams.limitBytes(stream, _)).getOrElse(stream)
      }

      override def failedStream(e: => Throwable): streams.BinaryStream =
        ZStream.fail(e)

      override def emptyStream: streams.BinaryStream =
        ZStream.empty
    }
  }
}
