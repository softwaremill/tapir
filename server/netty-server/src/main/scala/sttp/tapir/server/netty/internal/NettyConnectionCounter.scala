package sttp.tapir.server.netty.internal

import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelHandlerContext, ChannelInboundHandlerAdapter}

import java.util.concurrent.atomic.AtomicInteger

@Sharable case class NettyConnectionCounter(maxConnections: Int) extends ChannelInboundHandlerAdapter {
  private val connections = new AtomicInteger()

  override def channelActive(ctx: ChannelHandlerContext): Unit = {
    val counter = connections.incrementAndGet
    if (counter <= maxConnections) super.channelActive(ctx) else ctx.close
  }

  override def channelInactive(ctx: ChannelHandlerContext): Unit = {
    super.channelInactive(ctx)
    connections.decrementAndGet
  }
}
