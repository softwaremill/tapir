package sttp.tapir.server.netty.internal.ws

import io.netty.channel.{ChannelDuplexHandler, ChannelFuture, ChannelFutureListener, ChannelHandlerContext, ChannelPromise}
import io.netty.handler.codec.http.{HttpResponse, HttpResponseStatus}
import io.netty.handler.codec.http.websocketx.{CloseWebSocketFrame, WebSocketCloseStatus, WebSocketFrame}
import io.netty.util.ReferenceCountUtil
import sttp.tapir.server.netty.internal.NettyServerHandler

import java.nio.channels.ClosedChannelException
import java.util.concurrent.atomic.AtomicBoolean

/** Closes a web socket with a 1001 (Going Away) close frame, when the server is shutting down (see
  * [[NettyServerHandler.ServerShuttingDown]]). Once any close frame is sent, drops further outgoing frames, and closes the connection when
  * the client's close frame arrives. Should be placed before all handlers which write web socket frames, and the handshake response.
  */
class WebSocketShutdownHandler(isShuttingDown: AtomicBoolean) extends ChannelDuplexHandler {
  private var handshakeSent = false
  private var closeSent: Option[ChannelFuture] = None

  override def userEventTriggered(ctx: ChannelHandlerContext, evt: Any): Unit = evt match {
    // before the handshake response, a close frame can't be encoded yet; it's then sent along with that response
    case NettyServerHandler.ServerShuttingDown => if (handshakeSent) sendGoingAway(ctx)
    case _                                     => super.userEventTriggered(ctx, evt)
  }

  override def write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise): Unit = msg match {
    case _: WebSocketFrame if closeSent.isDefined =>
      // as in Netty's WebSocketProtocolHandler: the protocol forbids sending frames after a close frame
      ReferenceCountUtil.release(msg): Unit
      val _ = promise.setFailure(new ClosedChannelException)
    case _: CloseWebSocketFrame =>
      // unvoid, so that a listener can be added
      val written = promise.unvoid()
      closeSent = Some(written)
      val _ = ctx.write(msg, written)
    case r: HttpResponse if r.status() == HttpResponseStatus.SWITCHING_PROTOCOLS =>
      handshakeSent = true
      val _ = ctx.write(msg, promise)
      // the shutdown might have started before the handshake response was written
      if (isShuttingDown.get()) sendGoingAway(ctx)
    case _ => val _ = ctx.write(msg, promise)
  }

  override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit = (msg, closeSent) match {
    case (_: CloseWebSocketFrame, Some(closeWritten)) =>
      // the closing handshake is complete; the server should close the connection first (RFC 6455, 7.1.1)
      ctx.fireChannelRead(msg): Unit
      val _ = closeWritten.addListener(ChannelFutureListener.CLOSE)
    case _ => val _ = ctx.fireChannelRead(msg)
  }

  private def sendGoingAway(ctx: ChannelHandlerContext): Unit =
    if (closeSent.isEmpty) {
      closeSent = Some(ctx.writeAndFlush(new CloseWebSocketFrame(WebSocketCloseStatus.ENDPOINT_UNAVAILABLE)))
    }
}
