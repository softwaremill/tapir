package sttp.tapir.server.netty.internal

import io.netty.handler.codec.http.HttpContent
import org.reactivestreams.Publisher
import sttp.capabilities
import sttp.model.HasHeaders
import sttp.monad.MonadError
import sttp.tapir.capabilities.NoStreams
import sttp.tapir.server.netty.NettyResponse
import sttp.tapir.server.netty.internal.NettyToResponseBody.DefaultChunkSize
import sttp.tapir.server.netty.internal.reactivestreams.InputStreamPublisher
import sttp.tapir.{CodecFormat, InputStreamRange, WebSocketBodyOutput}

import java.nio.charset.Charset

/** Common logic for producing response body from responses in all Netty backends that don't support streaming. These backends use our
  * custom reactive Publishers to integrate responses like InputStreamBody, InputStreamRangeBody or FileBody with Netty reactive extensions.
  * Other kinds of raw responses like directly available String, ByteArray or ByteBuffer can be returned without wrapping into a Publisher.
  */
private[netty] class NettyToResponseBody[F[_]](runAsync: RunAsync[F])(implicit me: MonadError[F])
    extends NettyToResponseBodyWrap[NoStreams] {

  override val streams: capabilities.Streams[NoStreams] = NoStreams

  protected def wrap(streamRange: InputStreamRange): Publisher[HttpContent] =
    new InputStreamPublisher[F](streamRange, DefaultChunkSize, runAsync)

  override def fromStreamValue(
      v: streams.BinaryStream,
      headers: HasHeaders,
      format: CodecFormat,
      charset: Option[Charset]
  ): NettyResponse = throw new UnsupportedOperationException

  override def fromWebSocketPipe[REQ, RESP](
      pipe: streams.Pipe[REQ, RESP],
      o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, NoStreams]
  ): NettyResponse = throw new UnsupportedOperationException
}

private[netty] object NettyToResponseBody {
  val DefaultChunkSize = 8192
}
