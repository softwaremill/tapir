package sttp.tapir.server.netty.internal

import sttp.capabilities.Streams
import io.netty.handler.codec.http.HttpContent
import org.reactivestreams.Publisher
import java.io.InputStream
import sttp.tapir.TapirFile
import sttp.tapir.FileRange

private[netty] trait StreamCompatible[S <: Streams[S]] {
  val streams: S
  def fromFile(file: FileRange): streams.BinaryStream
  def fromInputStream(is: () => InputStream, length: Option[Long]): streams.BinaryStream
  def fromNettyStream(s: Publisher[HttpContent]): streams.BinaryStream
  def asPublisher(s: streams.BinaryStream): Publisher[HttpContent]

  def publisherFromFile(file: FileRange): Publisher[HttpContent] = 
    asPublisher(fromFile(file))

  def publisherFromInputStream(is: () => InputStream, length: Option[Long]): Publisher[HttpContent] = 
    asPublisher(fromInputStream(is, length))
}
