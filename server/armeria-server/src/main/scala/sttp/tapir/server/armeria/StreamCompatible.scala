package sttp.tapir.server.armeria

import com.linecorp.armeria.common.HttpData
import org.reactivestreams.Publisher
import sttp.capabilities.Streams

private[armeria] trait StreamCompatible[S <: Streams[S]] {
  val streams: S
  def asStreamMessage(s: streams.BinaryStream): Publisher[HttpData]
  def fromArmeriaStream(s: Publisher[HttpData], maxBytes: Option[Long]): streams.BinaryStream
}
