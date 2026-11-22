package sttp.tapir.server.netty.internal

import io.netty.channel.{Channel, ChannelFuture}
import io.netty.channel.group.ChannelGroup
import io.netty.util.concurrent.{EventExecutor, EventExecutorGroup, Future => NettyFuture, PromiseNotifier}

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.duration._

object NettyShutdown {

  /** Stops the server gracefully: stops accepting connections, closes idle ones, and waits for the others to close, up to the graceful
    * shutdown timeout, then closes the remaining ones. Can be called more than once.
    *
    * @param scheduler
    *   used to time out the wait, without blocking any thread
    * @return
    *   completed once all connections are closed; failed if closing the server channel fails
    */
  def drain(
      serverChannel: Channel,
      channelGroup: ChannelGroup,
      scheduler: EventExecutor,
      isShuttingDown: AtomicBoolean,
      gracefulShutdownTimeout: Option[FiniteDuration]
  ): NettyFuture[Void] = {
    startDraining(channelGroup, isShuttingDown)
    val drained = scheduler.newPromise[Void]()
    // while draining, new connections are refused, so that e.g. a load balancer routes them to other instances
    val _ = serverChannel.close().addListener { (serverChannelClosed: ChannelFuture) =>
      val connectionsClosed = closeConnections(channelGroup, scheduler, gracefulShutdownTimeout)
      val _ =
        if (serverChannelClosed.isSuccess) connectionsClosed.addListener(new PromiseNotifier[Void, NettyFuture[Void]](drained))
        else connectionsClosed.addListener((_: NettyFuture[Void]) => { val _ = drained.tryFailure(serverChannelClosed.cause()) })
    }
    drained
  }

  /** Marks the server as shutting down, and fires [[NettyServerHandler.ServerShuttingDown]] on all connections. */
  private def startDraining(channelGroup: ChannelGroup, isShuttingDown: AtomicBoolean): Unit = {
    isShuttingDown.set(true)
    val channels = channelGroup.iterator()
    while (channels.hasNext) {
      val _ = channels.next().pipeline().fireUserEventTriggered(NettyServerHandler.ServerShuttingDown)
    }
  }

  /** Waits until the server's connections close, up to the graceful shutdown timeout, then closes the remaining ones. Call once the server
    * channel is closed.
    */
  private def closeConnections(
      channelGroup: ChannelGroup,
      scheduler: EventExecutor,
      gracefulShutdownTimeout: Option[FiniteDuration]
  ): NettyFuture[Void] = {
    val drained = scheduler.newPromise[Void]()
    def completeDrained(): Unit = { val _ = drained.trySuccess(null) }
    gracefulShutdownTimeout match {
      case Some(timeout) =>
        val timer =
          scheduler.schedule(new Runnable { override def run(): Unit = completeDrained() }, timeout.toMillis, TimeUnit.MILLISECONDS)
        // connections opened later are closed by NettyServerHandler, as the server is already shutting down
        val _ = channelGroup.newCloseFuture().addListener { (_: NettyFuture[Void]) =>
          completeDrained()
          val _ = timer.cancel(false)
        }
      case None => completeDrained()
    }
    val closed = scheduler.newPromise[Void]()
    val _ = drained.addListener { (_: NettyFuture[Void]) =>
      val _ = channelGroup.close().addListener(new PromiseNotifier[Void, NettyFuture[Void]](closed))
    }
    closed
  }

  private val ExecutorShutdownTimeout = 15.seconds

  /** Shuts down the given executor (group). Call once the server's channels are closed.
    *
    * @param quietPeriod
    *   tasks submitted within this period still run (Netty's default is 2 seconds). Non-zero if requests might still be running, e.g. past
    *   the graceful shutdown timeout, so that their cleanup still runs on the event loop.
    */
  def shutdownExecutor(executor: EventExecutorGroup, quietPeriod: FiniteDuration = 100.millis): NettyFuture[_] =
    executor.shutdownGracefully(quietPeriod.toMillis, ExecutorShutdownTimeout.toMillis, TimeUnit.MILLISECONDS)
}
