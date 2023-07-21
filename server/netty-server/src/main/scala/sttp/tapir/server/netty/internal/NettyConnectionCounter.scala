package sttp.tapir.server.netty.internal

import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelHandlerContext, ChannelInboundHandlerAdapter}
import io.netty.util.internal.logging.InternalLoggerFactory

import java.util.concurrent.atomic.AtomicInteger

@Sharable case class NettyConnectionCounter(maxConnections: Int) extends ChannelInboundHandlerAdapter {
  private val connections = new AtomicInteger()
  private lazy val logger = InternalLoggerFactory.getInstance(getClass)

  override def channelActive(ctx: ChannelHandlerContext): Unit = {
    val counter = connections.incrementAndGet
    if (counter <= maxConnections) super.channelActive(ctx)
    else {
      logger.warn(s"Max connections exceeded: $maxConnections")
      ctx.close
    }
  }

  override def channelInactive(ctx: ChannelHandlerContext): Unit = {
    super.channelInactive(ctx)
    connections.decrementAndGet
  }
}
