package sttp.tapir.server.netty.loom.internal

import _root_.ox.*
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame
import io.netty.handler.codec.http.websocketx.WebSocketCloseStatus
import sttp.capabilities
import sttp.model.HasHeaders
import sttp.monad.MonadError
import sttp.tapir.server.interpreter.ToResponseBody
import sttp.tapir.server.netty.NettyResponse
import sttp.tapir.server.netty.NettyResponseContent.ReactiveWebSocketProcessorNettyResponseContent
import sttp.tapir.server.netty.internal.{NettyToResponseBody, RunAsync}
import sttp.tapir.server.netty.loom._
import sttp.tapir.server.netty.loom.internal.ox.OxDispatcher
import sttp.tapir.*

import java.nio.charset.Charset

private[loom] class NettyIdToResponseBody(runAsync: RunAsync[Id])(using me: MonadError[Id], ox: Ox)
    extends ToResponseBody[NettyResponse, OxStreams] {

  private val oxDispatcher = new OxDispatcher
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
    val channelPromise = ctx.newPromise()
    new ReactiveWebSocketProcessorNettyResponseContent(
      channelPromise,
      ws.OxSourceWebSocketProcessor[REQ, RESP](
        oxDispatcher,
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
