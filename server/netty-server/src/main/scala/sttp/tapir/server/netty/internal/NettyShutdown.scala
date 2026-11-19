package sttp.tapir.server.netty.internal

import io.netty.channel.group.ChannelGroup
import io.netty.util.concurrent.{EventExecutorGroup, Future => NettyFuture}

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object NettyShutdown {

  /** Marks the server as shutting down, and closes idle connections (see [[NettyServerHandler]]). Should be followed by closing the server
    * channel, so that while draining, new connections are refused (and e.g. routed by a load balancer to other instances).
    */
  def startDraining(channelGroup: ChannelGroup, isShuttingDown: AtomicBoolean): Unit = {
    isShuttingDown.set(true)
    val channels = channelGroup.iterator()
    while (channels.hasNext) {
      val _ = channels.next().pipeline().fireUserEventTriggered(NettyServerHandler.CloseIfIdle)
    }
  }

  /** Shuts down the given executor (group), once the server's channels are closed. Tasks that are already queued still run.
    *
    * @param quietPeriodMillis
    *   tasks submitted within this period still run (Netty's default is 2 seconds). Non-zero if requests might still be running, e.g. past
    *   the graceful shutdown timeout, so that their cleanup still runs on the event loop.
    */
  def shutdownExecutor(executor: EventExecutorGroup, quietPeriodMillis: Long = 100): NettyFuture[_] =
    executor.shutdownGracefully(quietPeriodMillis, 15000, TimeUnit.MILLISECONDS)
}
