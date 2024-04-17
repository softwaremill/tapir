package sttp.tapir.server.netty.loom

import io.netty.channel.Channel
import io.netty.channel.EventLoopGroup
import io.netty.channel.group.{ChannelGroup, DefaultChannelGroup}
import io.netty.channel.unix.DomainSocketAddress
import io.netty.util.concurrent.DefaultEventExecutor
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.model.ServerResponse
import sttp.tapir.server.netty.NettyConfig
import sttp.tapir.server.netty.NettyResponse
import sttp.tapir.server.netty.Route
import sttp.tapir.server.netty.internal.NettyBootstrap
import sttp.tapir.server.netty.internal.NettyServerHandler

import java.net.InetSocketAddress
import java.net.SocketAddress
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.{Future => JFuture}
import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal

case class NettySyncServer(routes: Vector[IdRoute], options: NettySyncServerOptions, config: NettyConfig) {
  private val executor = Executors.newVirtualThreadPerTaskExecutor()

  def addEndpoint(se: ServerEndpoint[Any, Id]): NettySyncServer = addEndpoints(List(se))
  def addEndpoint(se: ServerEndpoint[Any, Id], overrideOptions: NettySyncServerOptions): NettySyncServer =
    addEndpoints(List(se), overrideOptions)
  def addEndpoints(ses: List[ServerEndpoint[Any, Id]]): NettySyncServer = addRoute(NettySyncServerInterpreter(options).toRoute(ses))
  def addEndpoints(ses: List[ServerEndpoint[Any, Id]], overrideOptions: NettySyncServerOptions): NettySyncServer =
    addRoute(NettySyncServerInterpreter(overrideOptions).toRoute(ses))

  def addRoute(r: IdRoute): NettySyncServer = copy(routes = routes :+ r)
  def addRoutes(r: Iterable[IdRoute]): NettySyncServer = copy(routes = routes ++ r)

  def options(o: NettySyncServerOptions): NettySyncServer = copy(options = o)
  def config(c: NettyConfig): NettySyncServer = copy(config = c)
  def modifyConfig(f: NettyConfig => NettyConfig): NettySyncServer = config(f(config))

  def host(hostname: String): NettySyncServer = modifyConfig(_.host(hostname))

  def port(p: Int): NettySyncServer = modifyConfig(_.port(p))

  def start(): NettySyncServerBinding =
    startUsingSocketOverride[InetSocketAddress](None) match {
      case (socket, stop) =>
        NettySyncServerBinding(socket, stop)
    }

  def startUsingDomainSocket(path: Option[Path] = None): NettySyncDomainSocketBinding =
    startUsingDomainSocket(path.getOrElse(Paths.get(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString)))

  def startUsingDomainSocket(path: Path): NettySyncDomainSocketBinding =
    startUsingSocketOverride(Some(new DomainSocketAddress(path.toFile))) match {
      case (socket, stop) =>
        NettySyncDomainSocketBinding(socket, stop)
    }

  private def startUsingSocketOverride[SA <: SocketAddress](socketOverride: Option[SA]): (SA, () => Unit) = {
    val eventLoopGroup = config.eventLoopConfig.initEventLoopGroup()
    val route = Route.combine(routes)

    def unsafeRunF(
        callToExecute: () => Id[ServerResponse[NettyResponse]]
    ): (Future[ServerResponse[NettyResponse]], () => Future[Unit]) = {
      val scalaPromise = Promise[ServerResponse[NettyResponse]]()
      val jFuture: JFuture[?] = executor.submit(new Runnable {
        override def run(): Unit = try {
          val result = callToExecute()
          scalaPromise.success(result)
        } catch {
          case NonFatal(e) => scalaPromise.failure(e)
        }
      })

      (
        scalaPromise.future,
        () => {
          jFuture.cancel(true)
          Future.unit
        }
      )
    }
    val eventExecutor = new DefaultEventExecutor()
    val channelGroup = new DefaultChannelGroup(eventExecutor) // thread safe
    val isShuttingDown: AtomicBoolean = new AtomicBoolean(false)

    val channelIdFuture = NettyBootstrap(
      config,
      new NettyServerHandler(
        route,
        unsafeRunF,
        channelGroup,
        isShuttingDown,
        config.serverHeader,
        config.isSsl
      ),
      eventLoopGroup,
      socketOverride
    )
    try {
      channelIdFuture.sync()
      val channelId = channelIdFuture.channel()
      (
        channelId.localAddress().asInstanceOf[SA],
        () => stop(channelId, eventLoopGroup, channelGroup, eventExecutor, isShuttingDown, config.gracefulShutdownTimeout)
      )
    } catch {
      case NonFatal(startFailureCause) =>
        try {
          stopRecovering(eventLoopGroup, channelGroup, eventExecutor, isShuttingDown, config.gracefulShutdownTimeout)
        } catch {
          case NonFatal(recoveryFailureCause) => startFailureCause.addSuppressed(recoveryFailureCause)
        }
        throw startFailureCause
    }
  }

  private def waitForClosedChannels(
      channelGroup: ChannelGroup,
      startNanos: Long,
      gracefulShutdownTimeoutNanos: Option[Long]
  ): Unit = {
    while (!channelGroup.isEmpty && gracefulShutdownTimeoutNanos.exists(_ >= System.nanoTime() - startNanos)) {
      Thread.sleep(100)
    }
    val _ = channelGroup.close().get()
  }

  private def stop(
      ch: Channel,
      eventLoopGroup: EventLoopGroup,
      channelGroup: ChannelGroup,
      eventExecutor: DefaultEventExecutor,
      isShuttingDown: AtomicBoolean,
      gracefulShutdownTimeout: Option[FiniteDuration]
  ): Unit = {
    isShuttingDown.set(true)
    waitForClosedChannels(
      channelGroup,
      startNanos = System.nanoTime(),
      gracefulShutdownTimeoutNanos = gracefulShutdownTimeout.map(_.toNanos)
    )
    ch.close().get()
    if (config.shutdownEventLoopGroupOnClose) {
      val _ = eventLoopGroup.shutdownGracefully().get()
      val _ = eventExecutor.shutdownGracefully().get()
    }
  }

  private def stopRecovering(
      eventLoopGroup: EventLoopGroup,
      channelGroup: ChannelGroup,
      eventExecutor: DefaultEventExecutor,
      isShuttingDown: AtomicBoolean,
      gracefulShutdownTimeout: Option[FiniteDuration]
  ): Unit = {
    isShuttingDown.set(true)
    waitForClosedChannels(
      channelGroup,
      startNanos = System.nanoTime(),
      gracefulShutdownTimeoutNanos = gracefulShutdownTimeout.map(_.toNanos)
    )
    if (config.shutdownEventLoopGroupOnClose) {
      val _ = eventLoopGroup.shutdownGracefully().get()
      val _ = eventExecutor.shutdownGracefully().get()
    }
  }
}

object NettySyncServer {
  def apply(): NettySyncServer = NettySyncServer(Vector.empty, NettySyncServerOptions.default, NettyConfig.default)

  def apply(serverOptions: NettySyncServerOptions): NettySyncServer =
    NettySyncServer(Vector.empty, serverOptions, NettyConfig.default)

  def apply(config: NettyConfig): NettySyncServer =
    NettySyncServer(Vector.empty, NettySyncServerOptions.default, config)

  def apply(serverOptions: NettySyncServerOptions, config: NettyConfig): NettySyncServer =
    NettySyncServer(Vector.empty, serverOptions, config)
}
case class NettySyncServerBinding(localSocket: InetSocketAddress, stop: () => Unit) {
  def hostName: String = localSocket.getHostName
  def port: Int = localSocket.getPort
}
case class NettySyncDomainSocketBinding(localSocket: DomainSocketAddress, stop: () => Unit) {
  def path: String = localSocket.path()
}
