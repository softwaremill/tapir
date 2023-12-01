package sttp.tapir.server.netty.internal

import io.netty.handler.codec.http.HttpContent
import org.reactivestreams.Publisher
import sttp.capabilities.Streams
import sttp.tapir.FileRange

import java.io.InputStream

private[netty] trait StreamCompatible[S <: Streams[S]] {
  val streams: S
  def fromFile(file: FileRange): streams.BinaryStream
  def fromInputStream(is: () => InputStream, length: Option[Long]): streams.BinaryStream
  def fromPublisher(publisher: Publisher[HttpContent], maxBytes: Option[Long]): streams.BinaryStream
  def asPublisher(s: streams.BinaryStream): Publisher[HttpContent]

  def failedStream(e: => Throwable): streams.BinaryStream
  def emptyStream: streams.BinaryStream

  def publisherFromFile(file: FileRange): Publisher[HttpContent] =
    asPublisher(fromFile(file))

  def publisherFromInputStream(is: () => InputStream, length: Option[Long]): Publisher[HttpContent] =
    asPublisher(fromInputStream(is, length))
}
