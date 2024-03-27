package sttp.tapir.server.netty.internal

import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame

/** Handles Ping, Pong, and Close frames for WebSockets.
  */
class NettyControlFrameHandler(ignorePong: Boolean, autoPongOnPing: Boolean, decodeCloseRequests: Boolean)
    extends ChannelInboundHandlerAdapter {

  override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit = {
    msg match {
      case ping: PingWebSocketFrame =>
        if (autoPongOnPing) {
          val _ = ctx.writeAndFlush(new PongWebSocketFrame(ping.content().retain()))
        } else {
          val _ = ping.content().release()
        }
      case pong: PongWebSocketFrame =>
        if (!ignorePong) {
          val _ = ctx.fireChannelRead(pong)
        } else {
          val _ = pong.content().release()
        }
      case close: CloseWebSocketFrame =>
        if (decodeCloseRequests) {
          // Passing the Close frame for further processing
          val _ = ctx.fireChannelRead(close)
        } else {
          // Responding with Close immediately
          val _ = ctx.writeAndFlush(close)
        }
      case other =>
        val _ = ctx.fireChannelRead(other)
    }
  }
}
