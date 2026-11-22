package sttp.tapir.server.netty.internal

import io.netty.channel.{Channel, ChannelFuture, EventLoopGroup}
import io.netty.channel.group.ChannelGroup
import io.netty.util.concurrent.{EventExecutor, EventExecutorGroup, Future => NettyFuture, GlobalEventExecutor, PromiseNotifier}

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.duration._

private[netty] object NettyShutdown {

  /** A user event, fired on all connections when the server starts shutting down. Idle HTTP connections are then closed, and web sockets
    * get a close frame.
    */
  case object ServerShuttingDown

  /** Stops the server gracefully: stops accepting connections, closes idle ones, and waits for the others to close, up to the graceful
    * shutdown timeout, then closes the remaining ones.
    *
    * @param scheduler
    *   used to time out the wait, without blocking any thread
    * @return
    *   completed once all connections are closed; failed if closing the server channel, or a connection, fails
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

  /** Marks the server as shutting down, and fires [[ServerShuttingDown]] on all connections. */
  private def startDraining(channelGroup: ChannelGroup, isShuttingDown: AtomicBoolean): Unit = {
    isShuttingDown.set(true)
    val channels = channelGroup.iterator()
    while (channels.hasNext) {
      val _ = channels.next().pipeline().fireUserEventTriggered(ServerShuttingDown)
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
    val waitOver = scheduler.newPromise[Void]()
    def endWait(): Unit = { val _ = waitOver.trySuccess(null) }
    gracefulShutdownTimeout match {
      case Some(timeout) =>
        val timer =
          scheduler.schedule(new Runnable { override def run(): Unit = endWait() }, timeout.toMillis, TimeUnit.MILLISECONDS)
        // connections opened later are closed by NettyServerHandler, as the server is already shutting down
        val _ = channelGroup.newCloseFuture().addListener { (_: NettyFuture[Void]) =>
          endWait()
          val _ = timer.cancel(false)
        }
      case None => endWait()
    }
    val closed = scheduler.newPromise[Void]()
    val _ = waitOver.addListener { (_: NettyFuture[Void]) =>
      val _ = channelGroup.close().addListener(new PromiseNotifier[Void, NettyFuture[Void]](closed))
    }
    closed
  }

  private val ExecutorShutdownTimeout = 15.seconds

  /** For an event loop group on which requests might still be running, e.g. past the graceful shutdown timeout, so that their cleanup still
    * runs.
    */
  val EventLoopGroupQuietPeriod: FiniteDuration = 100.millis

  /** Shuts down the server's executors. Call once the server's channels are closed.
    *
    * @param eventLoopGroup
    *   `None` if it's not to be shut down
    * @param eventLoopGroupQuietPeriod
    *   tasks submitted within this period still run (Netty's default is 2 seconds)
    */
  def shutdownExecutors(
      eventLoopGroup: Option[EventLoopGroup],
      eventExecutor: EventExecutor,
      eventLoopGroupQuietPeriod: FiniteDuration
  ): NettyFuture[Void] = {
    val done = GlobalEventExecutor.INSTANCE.newPromise[Void]()
    // the listeners ignore the outcome, as termination futures never fail
    def shutdownEventExecutor(): Unit = {
      // no quiet period, as the event executor is used only when stopping
      val _ = shutdownGracefully(eventExecutor, Duration.Zero).addListener((_: NettyFuture[Any]) => { val _ = done.trySuccess(null) })
    }
    eventLoopGroup match {
      case Some(group) =>
        val _ = shutdownGracefully(group, eventLoopGroupQuietPeriod).addListener((_: NettyFuture[Any]) => shutdownEventExecutor())
      case None => shutdownEventExecutor()
    }
    done
  }

  private def shutdownGracefully(executor: EventExecutorGroup, quietPeriod: FiniteDuration): NettyFuture[Any] =
    executor
      .shutdownGracefully(quietPeriod.toMillis, ExecutorShutdownTimeout.toMillis, TimeUnit.MILLISECONDS)
      .asInstanceOf[NettyFuture[Any]]
}
