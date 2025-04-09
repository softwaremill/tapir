package sttp.tapir.server.netty.cats

import cats.effect.kernel.Sync
import cats.effect.std.Dispatcher
import cats.effect.{Async, IO, Resource, Temporal}
import cats.syntax.all._
import io.netty.channel._
import io.netty.channel.group.{ChannelGroup, DefaultChannelGroup}
import io.netty.channel.unix.DomainSocketAddress
import io.netty.util.concurrent.DefaultEventExecutor
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.monad.MonadError
import sttp.tapir.integ.cats.effect.CatsMonadError
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.model.ServerResponse
import sttp.tapir.server.netty.cats.internal.CatsUtil.{nettyChannelFutureToScala, nettyFutureToScala}
import sttp.tapir.server.netty.internal.{NettyBootstrap, NettyServerHandler}
import sttp.tapir.server.netty.{NettyConfig, NettyResponse, Route}

import java.net.{InetSocketAddress, SocketAddress}
import java.nio.file.{Path, Paths}
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.Future
import scala.concurrent.duration._

case class NettyCatsServer[F[_]: Async](
    serverEndpoints: Vector[ServerEndpoint[Fs2Streams[F] with WebSockets, F]],
    otherRoutes: Vector[Route[F]],
    options: NettyCatsServerOptions[F],
    config: NettyConfig
) {
  def addEndpoint(se: ServerEndpoint[Fs2Streams[F] with WebSockets, F]): NettyCatsServer[F] = addEndpoints(List(se))
  def addEndpoints(ses: List[ServerEndpoint[Fs2Streams[F] with WebSockets, F]]): NettyCatsServer[F] =
    copy(serverEndpoints = serverEndpoints ++ ses)

  /** Adds a custom route to the server. When a request is received, it is first processed by routes generated from the defined endpoints
    * (see [[addEndpoint]] and [[addEndpoints]] for the primary methods of defining server behavior). If none of these endpoints match the
    * request, and the [[RejectHandler]] is configured to allow fallback handling (see below), the request will then be processed by the
    * custom routes added using this method, in the order they were added.
    *
    * By default, the [[NettyCatsServerOptions]] are configured to return a `404` response when no endpoints match a request. This behavior
    * is controlled by the [[RejectHandler]]. If you intend to handle unmatched requests using custom routes, ensure that the
    * [[RejectHandler]] is configured appropriately to allow such fallback handling.
    */
  def addRoute(r: Route[F]): NettyCatsServer[F] = copy(otherRoutes = otherRoutes :+ r)

  /** Adds custom routes to the server. When a request is received, it is first processed by routes generated from the defined endpoints
    * (see [[addEndpoint]] and [[addEndpoints]] for the primary methods of defining server behavior). If none of these endpoints match the
    * request, and the [[RejectHandler]] is configured to allow fallback handling (see below), the request will then be processed by the
    * custom routes added using this method, in the order they were added.
    *
    * By default, the [[NettyCatsServerOptions]] are configured to return a `404` response when no endpoints match a request. This behavior
    * is controlled by the [[RejectHandler]]. If you intend to handle unmatched requests using custom routes, ensure that the
    * [[RejectHandler]] is configured appropriately to allow such fallback handling.
    */
  def addRoutes(r: Iterable[Route[F]]): NettyCatsServer[F] = copy(otherRoutes = otherRoutes ++ r)

  def options(o: NettyCatsServerOptions[F]): NettyCatsServer[F] = copy(options = o)

  def config(c: NettyConfig): NettyCatsServer[F] = copy(config = c)
  def modifyConfig(f: NettyConfig => NettyConfig): NettyCatsServer[F] = config(f(config))

  def host(h: String): NettyCatsServer[F] = modifyConfig(_.host(h))

  def port(p: Int): NettyCatsServer[F] = modifyConfig(_.port(p))

  def start(): F[NettyCatsServerBinding[F]] =
    startUsingSocketOverride[InetSocketAddress](None).map { case (socket, stop) =>
      NettyCatsServerBinding(socket, stop)
    }

  def startUsingDomainSocket(path: Option[Path] = None): F[NettyCatsDomainSocketBinding[F]] =
    startUsingDomainSocket(path.getOrElse(Paths.get(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString)))

  def startUsingDomainSocket(path: Path): F[NettyCatsDomainSocketBinding[F]] =
    startUsingSocketOverride(Some(new DomainSocketAddress(path.toFile))).map { case (socket, stop) =>
      NettyCatsDomainSocketBinding(socket, stop)
    }

  private def unsafeRunAsync(block: () => F[ServerResponse[NettyResponse]]): (Future[ServerResponse[NettyResponse]], () => Future[Unit]) =
    options.dispatcher.unsafeToFutureCancelable(block())

  private def startUsingSocketOverride[SA <: SocketAddress](socketOverride: Option[SA]): F[(SA, () => F[Unit])] = {
    val eventLoopGroup = config.eventLoopConfig.initEventLoopGroup()
    implicit val monadError: MonadError[F] = new CatsMonadError[F]()
    val endpointRoute = NettyCatsServerInterpreter(options).toRoute(serverEndpoints.toList)
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
          .flatMap(_ => Async[F].raiseError(e))
      }
  }

  private def waitForClosedChannels(
      channelGroup: ChannelGroup,
      startNanos: Long,
      gracefulShutdownTimeoutNanos: Option[Long]
  ): F[Unit] =
    if (!channelGroup.isEmpty && gracefulShutdownTimeoutNanos.exists(_ >= System.nanoTime() - startNanos)) {
      Temporal[F].sleep(100.millis) >>
        waitForClosedChannels(channelGroup, startNanos, gracefulShutdownTimeoutNanos)
    } else {
      Sync[F].delay(nettyFutureToScala(channelGroup.close())).void
    }

  private def stop(
      ch: Channel,
      eventLoopGroup: EventLoopGroup,
      channelGroup: ChannelGroup,
      eventExecutor: DefaultEventExecutor,
      isShuttingDown: AtomicBoolean,
      gracefulShutdownTimeout: Option[FiniteDuration]
  ): F[Unit] = {
    shutdownChannelGroup(channelGroup, isShuttingDown, gracefulShutdownTimeout) >>
      Async[F].defer {
        nettyFutureToScala(ch.close()).flatMap { _ => stopEventLoopGroup(eventLoopGroup, eventExecutor) }
      }
  }

  private def stopRecovering(
      eventLoopGroup: EventLoopGroup,
      channelGroup: ChannelGroup,
      eventExecutor: DefaultEventExecutor,
      isShuttingDown: AtomicBoolean,
      gracefulShutdownTimeout: Option[FiniteDuration]
  ): F[Unit] = {
    shutdownChannelGroup(channelGroup, isShuttingDown, gracefulShutdownTimeout) >>
      stopEventLoopGroup(eventLoopGroup, eventExecutor)
  }

  private def shutdownChannelGroup(
      channelGroup: ChannelGroup,
      isShuttingDown: AtomicBoolean,
      gracefulShutdownTimeout: Option[FiniteDuration]
  ) = {
    Sync[F].delay(isShuttingDown.set(true)) >>
      waitForClosedChannels(
        channelGroup,
        startNanos = System.nanoTime(),
        gracefulShutdownTimeoutNanos = gracefulShutdownTimeout.map(_.toNanos)
      )
  }

  private def stopEventLoopGroup(eventLoopGroup: EventLoopGroup, eventExecutor: DefaultEventExecutor) = {
    if (config.shutdownEventLoopGroupOnClose) {
      nettyFutureToScala(eventLoopGroup.shutdownGracefully())
        .flatMap(_ => nettyFutureToScala(eventExecutor.shutdownGracefully()).map(_ => ()))
    } else Async[F].unit
  }
}

object NettyCatsServer {
  def apply[F[_]: Async](dispatcher: Dispatcher[F]): NettyCatsServer[F] =
    NettyCatsServer(Vector.empty, Vector.empty, NettyCatsServerOptions.default(dispatcher), NettyConfig.default)
  def apply[F[_]: Async](options: NettyCatsServerOptions[F]): NettyCatsServer[F] =
    NettyCatsServer(Vector.empty, Vector.empty, options, NettyConfig.default)
  def apply[F[_]: Async](dispatcher: Dispatcher[F], config: NettyConfig): NettyCatsServer[F] =
    NettyCatsServer(Vector.empty, Vector.empty, NettyCatsServerOptions.default(dispatcher), config)
  def apply[F[_]: Async](options: NettyCatsServerOptions[F], config: NettyConfig): NettyCatsServer[F] =
    NettyCatsServer(Vector.empty, Vector.empty, options, config)

  def io(): Resource[IO, NettyCatsServer[IO]] = Dispatcher.parallel[IO].map(apply[IO](_))
  def io(config: NettyConfig): Resource[IO, NettyCatsServer[IO]] = Dispatcher.parallel[IO].map(apply[IO](_, config))
}

case class NettyCatsServerBinding[F[_]](localSocket: InetSocketAddress, stop: () => F[Unit]) {
  def hostName: String = localSocket.getHostName
  def port: Int = localSocket.getPort
}

case class NettyCatsDomainSocketBinding[F[_]](localSocket: DomainSocketAddress, stop: () => F[Unit]) {
  def path: String = localSocket.path()
}
