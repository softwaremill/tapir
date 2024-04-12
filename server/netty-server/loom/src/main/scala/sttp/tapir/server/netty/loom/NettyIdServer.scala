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
import sttp.capabilities.WebSockets
import ox.Ox

case class NettyIdServerEndpointListOverridenOptions(ses: List[ServerEndpoint[OxStreams with WebSockets, Id]], overridenOptions: NettyIdServerOptions) 
case class NettyIdServer(endpoints: List[ServerEndpoint[OxStreams with WebSockets, Id]], endpointsWithOptions: List[NettyIdServerEndpointListOverridenOptions], options: NettyIdServerOptions, config: NettyConfig) {
  private val executor = Executors.newVirtualThreadPerTaskExecutor()

  def addEndpoint(se: ServerEndpoint[OxStreams with WebSockets, Id]): NettyIdServer = addEndpoints(List(se))
  def addEndpoint(se: ServerEndpoint[OxStreams with WebSockets, Id], overrideOptions: NettyIdServerOptions): NettyIdServer =
    addEndpoints(List(se), overrideOptions)
  def addEndpoints(ses: List[ServerEndpoint[OxStreams with WebSockets, Id]]): NettyIdServer = copy(endpoints = endpoints ++ ses)
  def addEndpoints(ses: List[ServerEndpoint[OxStreams with WebSockets, Id]], overrideOptions: NettyIdServerOptions): NettyIdServer =
    copy(endpointsWithOptions = endpointsWithOptions :+ NettyIdServerEndpointListOverridenOptions(ses, overrideOptions))

  def options(o: NettyIdServerOptions): NettyIdServer = copy(options = o)
  def config(c: NettyConfig): NettyIdServer = copy(config = c)
  def modifyConfig(f: NettyConfig => NettyConfig): NettyIdServer = config(f(config))

  def host(hostname: String): NettyIdServer = modifyConfig(_.host(hostname))

  def port(p: Int): NettyIdServer = modifyConfig(_.port(p))

  def start()(using Ox): NettyIdServerBinding =
    startUsingSocketOverride[InetSocketAddress](None) match {
      case (socket, stop) =>
        NettyIdServerBinding(socket, stop)
    }

  private[netty] def start(routes: List[Route[Id]]): NettyIdServerBinding = 
    startUsingSocketOverride[InetSocketAddress](routes, None) match {
      case (socket, stop) =>
        NettyIdServerBinding(socket, stop)
    }

  def startUsingDomainSocket(path: Option[Path] = None)(using Ox): NettyIdDomainSocketBinding =
    startUsingDomainSocket(path.getOrElse(Paths.get(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString)))

  def startUsingDomainSocket(path: Path)(using Ox): NettyIdDomainSocketBinding =
    startUsingSocketOverride(Some(new DomainSocketAddress(path.toFile))) match {
      case (socket, stop) =>
        NettyIdDomainSocketBinding(socket, stop)
    }

  private def startUsingSocketOverride[SA <: SocketAddress](socketOverride: Option[SA])(using Ox): (SA, () => Unit) = {
    val routes = NettyIdServerInterpreter(options).toRoute(endpoints) :: endpointsWithOptions.map(e => NettyIdServerInterpreter(e.overridenOptions).toRoute(e.ses))
    startUsingSocketOverride(routes, socketOverride)
  }
  private def startUsingSocketOverride[SA <: SocketAddress](routes: List[Route[Id]], socketOverride: Option[SA]): (SA, () => Unit) = {
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

object NettyIdServer {
  def apply(): NettyIdServer = NettyIdServer(List.empty, List.empty, NettyIdServerOptions.default, NettyConfig.default)

  def apply(serverOptions: NettyIdServerOptions): NettyIdServer =
    NettyIdServer(List.empty, List.empty, serverOptions, NettyConfig.default)

  def apply(config: NettyConfig): NettyIdServer =
    NettyIdServer(List.empty, List.empty, NettyIdServerOptions.default, config)

  def apply(serverOptions: NettyIdServerOptions, config: NettyConfig): NettyIdServer =
    NettyIdServer(List.empty, List.empty, serverOptions, config)
}
case class NettyIdServerBinding(localSocket: InetSocketAddress, stop: () => Unit) {
  def hostName: String = localSocket.getHostName
  def port: Int = localSocket.getPort
}
case class NettyIdDomainSocketBinding(localSocket: DomainSocketAddress, stop: () => Unit) {
  def path: String = localSocket.path()
}
