package sttp.tapir.server.netty.loom

import sttp.tapir.server.netty.internal.NettyToResponseBody
import sttp.monad.MonadError
import sttp.tapir.server.netty.internal.RunAsync
import sttp.tapir.server.netty.NettyResponse
import sttp.tapir.WebSocketBodyOutput
import sttp.capabilities
import sttp.tapir.server.interpreter.ToResponseBody
import sttp.tapir.*
import sttp.model.HasHeaders
import java.nio.charset.Charset
import sttp.tapir.server.netty.NettyResponseContent.ReactiveWebSocketProcessorNettyResponseContent
import io.netty.channel.ChannelHandlerContext
import ox.Ox

class NettyIdToResponseBody(runAsync: RunAsync[Id])(using me: MonadError[Id], ox: Ox) extends ToResponseBody[NettyResponse, OxStreams] {

  val delegate = new NettyToResponseBody(runAsync)(me)

  override val streams: capabilities.Streams[OxStreams] = OxStreams

  def fromRawValue[R](v: R, headers: HasHeaders, format: CodecFormat, bodyType: RawBodyType[R]): NettyResponse =
    delegate.fromRawValue(v, headers, format, bodyType)
  def fromStreamValue(v: streams.BinaryStream, headers: HasHeaders, format: CodecFormat, charset: Option[Charset]): NettyResponse =
    throw new UnsupportedOperationException

  override def fromWebSocketPipe[REQ, RESP](
      pipe: streams.Pipe[REQ, RESP],
      o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, ?, OxStreams]
  ): NettyResponse = (ctx: ChannelHandlerContext) =>
    new ReactiveWebSocketProcessorNettyResponseContent(
      ctx.newPromise(),
      () =>
        ws.OxSourceWebSocketProcessor[REQ, RESP](
          pipe.asInstanceOf[OxStreams.Pipe[REQ, RESP]],
          o.asInstanceOf[WebSocketBodyOutput[OxStreams.Pipe[REQ, RESP], REQ, RESP, ?, OxStreams]],
          ctx
        ),
      ignorePong = o.ignorePong,
      autoPongOnPing = o.autoPongOnPing,
      decodeCloseRequests = o.decodeCloseRequests,
      autoPing = o.autoPing
    )
}
