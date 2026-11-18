package sttp.tapir.server.netty.internal

import io.netty.channel.group.ChannelGroup
import io.netty.util.concurrent.{EventExecutorGroup, Future => NettyFuture}

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object NettyShutdown {

  /** Starts draining the server's connections: those with no requests in flight are closed at once; the others are closed once the
    * responses to their in-flight requests are sent. Requests received from now on aren't handled (see [[NettyServerHandler]]), and
    * connections accepted from now on are closed at once.
    *
    * Should be followed by closing the server's channel, before waiting for the connections to close, so that while draining, new
    * connections are refused (and e.g. routed by a load balancer to other instances).
    */
  def startDraining(channelGroup: ChannelGroup, isShuttingDown: AtomicBoolean): Unit = {
    isShuttingDown.set(true)
    val channels = channelGroup.iterator()
    while (channels.hasNext) {
      val _ = channels.next().pipeline().fireUserEventTriggered(NettyServerHandler.CloseIfIdle)
    }
  }

  /** Shuts down the given executor (group) without Netty's default 2-second quiet period. To be used once the server's channels are closed,
    * when no new tasks are expected; tasks that are already queued still run.
    */
  def shutdownExecutor(executor: EventExecutorGroup): NettyFuture[_] = executor.shutdownGracefully(0, 15, TimeUnit.SECONDS)
}
