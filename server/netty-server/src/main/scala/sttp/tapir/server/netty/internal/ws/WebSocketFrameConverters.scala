package sttp.tapir.server.netty.internal.ws

import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.handler.codec.http.websocketx._
import io.netty.handler.codec.http.websocketx.{WebSocketFrame => NettyWebSocketFrame}
import sttp.ws.WebSocketFrame

object WebSocketFrameConverters {

  def getBytes(buf: ByteBuf): Array[Byte] = {
    val bytes = new Array[Byte](buf.readableBytes())
    buf.readBytes(bytes)
    bytes
  }

  def nettyFrameToFrame(nettyFrame: NettyWebSocketFrame): WebSocketFrame = {
    nettyFrame match {
      case text: TextWebSocketFrame   => WebSocketFrame.Text(text.text, text.isFinalFragment, Some(text.rsv))
      case close: CloseWebSocketFrame => WebSocketFrame.Close(close.statusCode, close.reasonText)
      case ping: PingWebSocketFrame   => WebSocketFrame.Ping(getBytes(ping.content()))
      case pong: PongWebSocketFrame   => WebSocketFrame.Pong(getBytes(pong.content()))
      case _ => WebSocketFrame.Binary(getBytes(nettyFrame.content()), nettyFrame.isFinalFragment, Some(nettyFrame.rsv))
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

  type Accumulator = Option[Either[Array[Byte], String]]
  val accumulateFrameState: (Accumulator, WebSocketFrame) => (Accumulator, Option[WebSocketFrame]) = {
        case (None, f: WebSocketFrame.Ping)                                  => (None, Some(f))
        case (None, f: WebSocketFrame.Pong)                                  => (None, Some(f))
        case (None, f: WebSocketFrame.Close)                                 => (None, Some(f))
        case (None, f: WebSocketFrame.Data[_]) if f.finalFragment            => (None, Some(f))
        case (None, f: WebSocketFrame.Text)                                  => (Some(Right(f.payload)), None)
        case (None, f: WebSocketFrame.Binary)                                => (Some(Left(f.payload)), None)
        case (Some(Left(acc)), f: WebSocketFrame.Binary) if f.finalFragment  => (None, Some(f.copy(payload = acc ++ f.payload)))
        case (Some(Left(acc)), f: WebSocketFrame.Binary) if !f.finalFragment => (Some(Left(acc ++ f.payload)), None)
        case (Some(Right(acc)), f: WebSocketFrame.Binary) if f.finalFragment =>
          // Netty's ContinuationFrame is translated to Binary, so we need to handle a Binary frame received after accumulating Text
          (None, Some(WebSocketFrame.Text(payload = acc + new String(f.payload), finalFragment = true, rsv = f.rsv)))
        case (Some(Right(acc)), f: WebSocketFrame.Binary) if !f.finalFragment => (Some(Right(acc + new String(f.payload))), None)
        case (Some(Right(acc)), f: WebSocketFrame.Text) if f.finalFragment    => (None, Some(f.copy(payload = acc + f.payload)))
        case (Some(Right(acc)), f: WebSocketFrame.Text) if !f.finalFragment   => (Some(Right(acc + f.payload)), None)
        case (acc, f) => throw new IllegalStateException(s"Cannot accumulate web socket frames. Accumulator: $acc, frame: $f.")
  }
}
