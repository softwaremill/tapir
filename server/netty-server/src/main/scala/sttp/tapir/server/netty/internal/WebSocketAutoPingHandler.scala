package sttp.tapir.server.netty.internal

import io.netty.buffer.Unpooled
import io.netty.channel.{ChannelHandlerContext, ChannelInboundHandlerAdapter}
import io.netty.handler.codec.http.websocketx._
import org.slf4j.LoggerFactory

import java.util.concurrent.{ScheduledFuture, TimeUnit}
import scala.concurrent.duration.FiniteDuration

/** If auto ping is enabled for an endpoint, this handler will be plugged into the pipeline. Its responsibility is to manage start and stop
  * of the ping scheduler.
  * @param pingInterval
  *   time interval to be used between sending pings to the client.
  * @param frame
  *   desired content of the Ping frame, as specified in the Tapir endpoint output.
  */
class WebSocketAutoPingHandler(pingInterval: FiniteDuration, frame: sttp.ws.WebSocketFrame.Ping) extends ChannelInboundHandlerAdapter {
  val nettyFrame = new PingWebSocketFrame(Unpooled.copiedBuffer(frame.payload))
  private var pingTask: ScheduledFuture[_] = _

  private val logger = LoggerFactory.getLogger(getClass.getName)

  override def handlerAdded(ctx: ChannelHandlerContext): Unit = {
    super.handlerAdded(ctx)
    if (ctx.channel.isActive) {
      logger.debug(s"STARTING WebSocket Ping scheduler for channel ${ctx.channel}, interval = $pingInterval")
      val sendPing: Runnable = new Runnable {
        override def run(): Unit = {
          logger.trace(s"Sending PING WebSocket frame for channel ${ctx.channel}")
          val _ = ctx.writeAndFlush(nettyFrame.retain())
        }
      }
      pingTask =
        ctx.channel().eventLoop().scheduleAtFixedRate(sendPing, pingInterval.toMillis, pingInterval.toMillis, TimeUnit.MILLISECONDS)
    }
  }

  override def channelInactive(ctx: ChannelHandlerContext): Unit = {
    super.channelInactive(ctx)
    logger.debug(s"STOPPING WebSocket Ping scheduler for channel ${ctx.channel}")
    if (pingTask != null) {
      val _ = pingTask.cancel(false)
    }
  }
}
