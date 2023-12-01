package sttp.tapir.server.netty.internal

import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.FullHttpRequest
import org.playframework.netty.http.StreamedHttpRequest
import sttp.capabilities.Streams
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.netty.internal.reactivestreams.NettyRequestBody

trait NettyStreamingRequestBody[F[_], S <: Streams[S]] extends NettyRequestBody[F, S] {

  val streamCompatible: StreamCompatible[S]
  override val streams = streamCompatible.streams

  override def toStream(serverRequest: ServerRequest, maxBytes: Option[Long]): streams.BinaryStream =
    (serverRequest.underlying match {
      case r: FullHttpRequest if r.content() == Unpooled.EMPTY_BUFFER => // means EmptyHttpRequest, but that class is not public
        streamCompatible.emptyStream
      case publisher: StreamedHttpRequest =>
        streamCompatible.fromPublisher(publisher, maxBytes)
      case other =>
        streamCompatible.failedStream(new UnsupportedOperationException(s"Unexpected Netty request of type: ${other.getClass().getName()}"))
    }).asInstanceOf[streams.BinaryStream]
}
