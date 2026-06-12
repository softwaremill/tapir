package sttp.tapir.server.netty.internal

import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.multipart.HttpDataFactory
import org.playframework.netty.http.StreamedHttpRequest
import sttp.capabilities.Streams
import sttp.tapir.model.ServerRequest
import sttp.tapir.TapirFile

/** Common logic for processing streaming request body in all Netty backends which support streaming. */
private[netty] abstract class NettyStreamingRequestBody[F[_], S <: Streams[S]](
    multipartTempDirectory: Option[TapirFile],
    multipartMinSizeForDisk: Option[Long]
) extends NettyRequestBody[F, S] {

  protected lazy val httpDataFactory: HttpDataFactory = NettyHelper.createHttpDataFactory(multipartMinSizeForDisk, multipartTempDirectory)

  val streamCompatible: StreamCompatible[S]
  override val streams = streamCompatible.streams

  override def toStream(serverRequest: ServerRequest, maxBytes: Option[Long]): streams.BinaryStream =
    (serverRequest.underlying match {
      case r: FullHttpRequest if r.content() == Unpooled.EMPTY_BUFFER => // means EmptyHttpRequest, but that class is not public
        streamCompatible.emptyStream
      case publisher: StreamedHttpRequest =>
        streamCompatible.fromPublisher(publisher, maxBytes)
      case other =>
        streamCompatible.failedStream(new UnsupportedOperationException(s"Unexpected Netty request of type: ${other.getClass.getName}"))
    }).asInstanceOf[streams.BinaryStream] // Scala can't figure out that it's the same type as streamCompatible.streams.BinaryStream
}
