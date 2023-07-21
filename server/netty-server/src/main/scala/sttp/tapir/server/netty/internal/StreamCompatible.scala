package sttp.tapir.server.netty.internal

import sttp.capabilities.Streams
import io.netty.handler.codec.http.HttpContent
import org.reactivestreams.Publisher

private[netty] trait StreamCompatible[S <: Streams[S]] {
  val streams: S
  def asStreamMessage(s: streams.BinaryStream): Publisher[HttpContent]
  def fromArmeriaStream(s: Publisher[HttpContent]): streams.BinaryStream
}
