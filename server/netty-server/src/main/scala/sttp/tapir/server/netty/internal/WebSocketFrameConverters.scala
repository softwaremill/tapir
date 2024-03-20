package sttp.tapir.server.netty.internal

import io.netty.handler.codec.http.websocketx._
import io.netty.handler.codec.http.websocketx.{WebSocketFrame => NettyWebSocketFrame}
import sttp.ws.WebSocketFrame
import io.netty.buffer.Unpooled

object WebSocketFrameConverters {

  def nettyFrameToFrame(nettyFrame: NettyWebSocketFrame): WebSocketFrame = {
    nettyFrame match {
      case text: TextWebSocketFrame   => WebSocketFrame.Text(text.text, text.isFinalFragment, Some(text.rsv))
      case close: CloseWebSocketFrame => WebSocketFrame.Close(close.statusCode, close.reasonText)
      case ping: PingWebSocketFrame   => WebSocketFrame.Ping(ping.content().nioBuffer().array())
      case pong: PongWebSocketFrame   => WebSocketFrame.Pong(pong.content().nioBuffer().array())
      case _ => WebSocketFrame.Binary(nettyFrame.content().nioBuffer().array(), nettyFrame.isFinalFragment, Some(nettyFrame.rsv))
    }
  }

  def frameToNettyFrame(w: WebSocketFrame): NettyWebSocketFrame = w match {
    case WebSocketFrame.Text(payload, finalFragment, rsvOpt) =>
      new TextWebSocketFrame(finalFragment, rsvOpt.getOrElse(0), payload)
    case WebSocketFrame.Close(statusCode, reasonText) =>
      new CloseWebSocketFrame(statusCode, reasonText)
    case WebSocketFrame.Ping(payload) =>
      new PingWebSocketFrame(Unpooled.wrappedBuffer(payload))
    case WebSocketFrame.Pong(payload) =>
      new PongWebSocketFrame(Unpooled.wrappedBuffer(payload))
    case WebSocketFrame.Binary(payload, finalFragment, rsvOpt) =>
      new BinaryWebSocketFrame(finalFragment, rsvOpt.getOrElse(0), Unpooled.wrappedBuffer(payload))
  }
}
