package sttp.tapir.server.netty.internal

import io.netty.handler.codec.http.HttpContent
import org.reactivestreams.Publisher
import sttp.capabilities.Streams
import sttp.tapir.FileRange

import java.io.InputStream

private[netty] trait StreamCompatible[S <: Streams[S]] {
  val streams: S
  def fromFile(file: FileRange, chunkSize: Int): streams.BinaryStream
  def fromInputStream(is: () => InputStream, chunkSize: Int, length: Option[Long]): streams.BinaryStream
  def fromPublisher(publisher: Publisher[HttpContent], maxBytes: Option[Long]): streams.BinaryStream
  def asPublisher(s: streams.BinaryStream): Publisher[HttpContent]

  def failedStream(e: => Throwable): streams.BinaryStream
  def emptyStream: streams.BinaryStream

  def publisherFromFile(file: FileRange, chunkSize: Int): Publisher[HttpContent] =
    asPublisher(fromFile(file, chunkSize))

  def publisherFromInputStream(is: () => InputStream, chunkSize: Int, length: Option[Long]): Publisher[HttpContent] =
    asPublisher(fromInputStream(is, chunkSize, length))
}
