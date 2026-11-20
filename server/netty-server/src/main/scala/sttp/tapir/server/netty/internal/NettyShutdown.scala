package sttp.tapir.server.netty.internal

import io.netty.channel.group.ChannelGroup
import io.netty.util.concurrent.{EventExecutorGroup, Future => NettyFuture}

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.duration._

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

  private val ShutdownTimeout = 15.seconds

  /** Shuts down the given executor (group). Call once the server's channels are closed.
    *
    * @param quietPeriod
    *   tasks submitted within this period still run (Netty's default is 2 seconds). Non-zero if requests might still be running, e.g. past
    *   the graceful shutdown timeout, so that their cleanup still runs on the event loop.
    */
  def shutdownExecutor(executor: EventExecutorGroup, quietPeriod: FiniteDuration = 100.millis): NettyFuture[_] =
    executor.shutdownGracefully(quietPeriod.toMillis, ShutdownTimeout.toMillis, TimeUnit.MILLISECONDS)
}
