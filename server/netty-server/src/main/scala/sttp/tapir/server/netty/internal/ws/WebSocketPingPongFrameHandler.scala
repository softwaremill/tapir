package sttp.tapir.server.netty.internal.ws

import io.netty.channel.{ChannelHandlerContext, ChannelInboundHandlerAdapter}
import io.netty.handler.codec.http.websocketx.{PingWebSocketFrame, PongWebSocketFrame}
import sttp.tapir.server.netty.internal._

/** Handles incoming Ping and Pong frames for WebSockets.
  */
class WebSocketPingPongFrameHandler(ignorePong: Boolean, autoPongOnPing: Boolean) extends ChannelInboundHandlerAdapter {

  override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit = {
    msg match {
      case ping: PingWebSocketFrame =>
        if (autoPongOnPing) {
          // retain the ping's content for the pong, then release the ping frame itself to avoid a ByteBuf leak
          val _ = ctx.writeAndFlush(new PongWebSocketFrame(ping.content().retain()))
          ping.release(): Unit
        } else {
          val _ = ctx.fireChannelRead(ping)
        }
      case pong: PongWebSocketFrame =>
        if (!ignorePong) {
          val _ = ctx.fireChannelRead(pong)
        } else {
          val _ = pong.content().release()
        }
      case other =>
        val _ = ctx.fireChannelRead(other)
    }
  }
}
