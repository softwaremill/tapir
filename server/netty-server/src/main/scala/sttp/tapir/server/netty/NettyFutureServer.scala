package sttp.tapir.server.netty

import io.netty.channel._
import io.netty.channel.group.{ChannelGroup, DefaultChannelGroup}
import io.netty.channel.unix.DomainSocketAddress
import io.netty.util.concurrent.DefaultEventExecutor
import sttp.monad.{FutureMonad, MonadError}
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.model.ServerResponse
import sttp.tapir.server.netty.internal.FutureUtil._
import sttp.tapir.server.netty.internal.{NettyBootstrap, NettyServerHandler}

import java.net.{InetSocketAddress, SocketAddress}
import java.nio.file.{Path, Paths}
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future, blocking}

case class NettyFutureServer(
    serverEndpoints: Vector[ServerEndpoint[Any, Future]],
    otherRoutes: Vector[FutureRoute],
    options: NettyFutureServerOptions,
    config: NettyConfig
)(implicit
    ec: ExecutionContext
) {
  def addEndpoint(se: ServerEndpoint[Any, Future]): NettyFutureServer = addEndpoints(List(se))
  def addEndpoints(ses: List[ServerEndpoint[Any, Future]]): NettyFutureServer = copy(serverEndpoints = serverEndpoints ++ ses)

  /** Adds a custom route to the server. When a request is received, it is first processed by routes generated from the defined endpoints
    * (see [[addEndpoint]] and [[addEndpoints]] for the primary methods of defining server behavior). If none of these endpoints match the
    * request, and the [[RejectHandler]] is configured to allow fallback handling (see below), the request will then be processed by the
    * custom routes added using this method, in the order they were added.
    *
    * By default, the [[NettyFutureServerOptions]] are configured to return a `404` response when no endpoints match a request. This
    * behavior is controlled by the [[RejectHandler]]. If you intend to handle unmatched requests using custom routes, ensure that the
    * [[RejectHandler]] is configured appropriately to allow such fallback handling.
    */
  def addRoute(r: FutureRoute): NettyFutureServer = copy(otherRoutes = otherRoutes :+ r)

  /** Adds custom routes to the server. When a request is received, it is first processed by routes generated from the defined endpoints
    * (see [[addEndpoint]] and [[addEndpoints]] for the primary methods of defining server behavior). If none of these endpoints match the
    * request, and the [[RejectHandler]] is configured to allow fallback handling (see below), the request will then be processed by the
    * custom routes added using this method, in the order they were added.
    *
    * By default, the [[NettyFutureServerOptions]] are configured to return a `404` response when no endpoints match a request. This
    * behavior is controlled by the [[RejectHandler]]. If you intend to handle unmatched requests using custom routes, ensure that the
    * [[RejectHandler]] is configured appropriately to allow such fallback handling.
    */
  def addRoutes(r: Iterable[FutureRoute]): NettyFutureServer = copy(otherRoutes = otherRoutes ++ r)

  def options(o: NettyFutureServerOptions): NettyFutureServer = copy(options = o)

  def config(c: NettyConfig): NettyFutureServer = copy(config = c)
  def modifyConfig(f: NettyConfig => NettyConfig): NettyFutureServer = config(f(config))

  def host(h: String): NettyFutureServer = modifyConfig(_.host(h))

  def port(p: Int): NettyFutureServer = modifyConfig(_.port(p))

  def start(): Future[NettyFutureServerBinding] =
    startUsingSocketOverride[InetSocketAddress](None).map { case (socket, stop) =>
      NettyFutureServerBinding(socket, stop)
    }

  def startUsingDomainSocket(path: Option[Path] = None): Future[NettyFutureDomainSocketBinding] =
    startUsingDomainSocket(path.getOrElse(Paths.get(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString)))

  def startUsingDomainSocket(path: Path): Future[NettyFutureDomainSocketBinding] =
    startUsingSocketOverride(Some(new DomainSocketAddress(path.toFile))).map { case (socket, stop) =>
      NettyFutureDomainSocketBinding(socket, stop)
    }

  private def unsafeRunAsync(
      block: () => Future[ServerResponse[NettyResponse]]
  ): (Future[ServerResponse[NettyResponse]], () => Future[Unit]) = {
    (block(), () => Future.unit) // noop cancellation handler, we can't cancel native Futures
  }

  private def startUsingSocketOverride[SA <: SocketAddress](socketOverride: Option[SA]): Future[(SA, () => Future[Unit])] = {
    val eventLoopGroup = config.eventLoopConfig.initEventLoopGroup()
    implicit val monadError: MonadError[Future] = new FutureMonad()
    val endpointRoute = NettyFutureServerInterpreter(options).toRoute(serverEndpoints.toList)
    val route = Route.combine(endpointRoute +: otherRoutes)
    val eventExecutor = new DefaultEventExecutor()
    val channelGroup = new DefaultChannelGroup(eventExecutor) // thread safe
    val isShuttingDown: AtomicBoolean = new AtomicBoolean(false)

    val channelFuture =
      NettyBootstrap(
        config,
        new NettyServerHandler(route, unsafeRunAsync, channelGroup, isShuttingDown, config),
        eventLoopGroup,
        socketOverride
      )

    nettyChannelFutureToScala(channelFuture)
      .map(ch =>
        (
          ch.localAddress().asInstanceOf[SA],
          () => stop(ch, eventLoopGroup, channelGroup, eventExecutor, isShuttingDown, config.gracefulShutdownTimeout)
        )
      )
      .recoverWith { case e =>
        stopRecovering(eventLoopGroup, channelGroup, eventExecutor, isShuttingDown, config.gracefulShutdownTimeout)
          .flatMap(_ => Future.failed(e))
      }
  }

  private def waitForClosedChannels(
      channelGroup: ChannelGroup,
      startNanos: Long,
      gracefulShutdownTimeoutNanos: Option[Long]
  ): Future[Unit] =
    if (!channelGroup.isEmpty && gracefulShutdownTimeoutNanos.exists(_ >= System.nanoTime() - startNanos)) {
      Future {
        blocking {
          Thread.sleep(100)
        }
      }.flatMap(_ => {
        waitForClosedChannels(channelGroup, startNanos, gracefulShutdownTimeoutNanos)
      })
    } else {
      nettyFutureToScala(channelGroup.close()).map(_ => ())
    }

  private def stop(
      ch: Channel,
      eventLoopGroup: EventLoopGroup,
      channelGroup: ChannelGroup,
      eventExecutor: DefaultEventExecutor,
      isShuttingDown: AtomicBoolean,
      gracefulShutdownTimeout: Option[FiniteDuration]
  ): Future[Unit] = {
    isShuttingDown.set(true)
    waitForClosedChannels(
      channelGroup,
      startNanos = System.nanoTime(),
      gracefulShutdownTimeoutNanos = gracefulShutdownTimeout.map(_.toNanos)
    ).flatMap { _ =>
      nettyFutureToScala(ch.close()).flatMap { _ => stopEventLoopGroup(eventLoopGroup, eventExecutor) }
    }
  }

  private def stopRecovering(
      eventLoopGroup: EventLoopGroup,
      channelGroup: ChannelGroup,
      eventExecutor: DefaultEventExecutor,
      isShuttingDown: AtomicBoolean,
      gracefulShutdownTimeout: Option[FiniteDuration]
  ): Future[Unit] = {
    isShuttingDown.set(true)
    waitForClosedChannels(
      channelGroup,
      startNanos = System.nanoTime(),
      gracefulShutdownTimeoutNanos = gracefulShutdownTimeout.map(_.toNanos)
    ).flatMap { _ => stopEventLoopGroup(eventLoopGroup, eventExecutor) }
  }

  private def stopEventLoopGroup(eventLoopGroup: EventLoopGroup, eventExecutor: DefaultEventExecutor) = {
    if (config.shutdownEventLoopGroupOnClose) {
      nettyFutureToScala(eventLoopGroup.shutdownGracefully())
        .flatMap(_ => nettyFutureToScala(eventExecutor.shutdownGracefully()).map(_ => ()))
    } else Future.successful(())
  }

}

object NettyFutureServer {
  def apply()(implicit ec: ExecutionContext): NettyFutureServer =
    NettyFutureServer(Vector.empty, Vector.empty, NettyFutureServerOptions.default, NettyConfig.default)

  def apply(serverOptions: NettyFutureServerOptions)(implicit ec: ExecutionContext): NettyFutureServer =
    NettyFutureServer(Vector.empty, Vector.empty, serverOptions, NettyConfig.default)

  def apply(config: NettyConfig)(implicit ec: ExecutionContext): NettyFutureServer =
    NettyFutureServer(Vector.empty, Vector.empty, NettyFutureServerOptions.default, config)

  def apply(serverOptions: NettyFutureServerOptions, config: NettyConfig)(implicit ec: ExecutionContext): NettyFutureServer =
    NettyFutureServer(Vector.empty, Vector.empty, serverOptions, config)
}

case class NettyFutureServerBinding(localSocket: InetSocketAddress, stop: () => Future[Unit]) {
  def hostName: String = localSocket.getHostName
  def port: Int = localSocket.getPort
}

case class NettyFutureDomainSocketBinding(localSocket: DomainSocketAddress, stop: () => Future[Unit]) {
  def path: String = localSocket.path()
}
