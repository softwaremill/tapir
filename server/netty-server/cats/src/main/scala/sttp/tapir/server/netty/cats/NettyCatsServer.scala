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

case class NettyCatsServer[F[_]: Async](routes: Vector[Route[F]], options: NettyCatsServerOptions[F], config: NettyConfig) {
  def addEndpoint(se: ServerEndpoint[Fs2Streams[F] with WebSockets, F]): NettyCatsServer[F] = addEndpoints(List(se))
  def addEndpoint(se: ServerEndpoint[Fs2Streams[F], F] with WebSockets, overrideOptions: NettyCatsServerOptions[F]): NettyCatsServer[F] =
    addEndpoints(List(se), overrideOptions)
  def addEndpoints(ses: List[ServerEndpoint[Fs2Streams[F] with WebSockets, F]]): NettyCatsServer[F] = addRoute(
    NettyCatsServerInterpreter(options).toRoute(ses)
  )
  def addEndpoints(
      ses: List[ServerEndpoint[Fs2Streams[F] with WebSockets, F]],
      overrideOptions: NettyCatsServerOptions[F]
  ): NettyCatsServer[F] = addRoute(
    NettyCatsServerInterpreter(overrideOptions).toRoute(ses)
  )

  def addRoute(r: Route[F]): NettyCatsServer[F] = copy(routes = routes :+ r)
  def addRoutes(r: Iterable[Route[F]]): NettyCatsServer[F] = copy(routes = routes ++ r)

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
    val route: Route[F] = Route.combine(routes)
    val eventExecutor = new DefaultEventExecutor()
    val channelGroup = new DefaultChannelGroup(eventExecutor) // thread safe
    val isShuttingDown: AtomicBoolean = new AtomicBoolean(false)

    val channelFuture =
      NettyBootstrap(
        config,
        new NettyServerHandler(route, unsafeRunAsync, channelGroup, isShuttingDown, config.serverHeader, config.isSsl),
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
    NettyCatsServer(Vector.empty, NettyCatsServerOptions.default(dispatcher), NettyConfig.default)
  def apply[F[_]: Async](options: NettyCatsServerOptions[F]): NettyCatsServer[F] =
    NettyCatsServer(Vector.empty, options, NettyConfig.default)
  def apply[F[_]: Async](dispatcher: Dispatcher[F], config: NettyConfig): NettyCatsServer[F] =
    NettyCatsServer(Vector.empty, NettyCatsServerOptions.default(dispatcher), config)
  def apply[F[_]: Async](options: NettyCatsServerOptions[F], config: NettyConfig): NettyCatsServer[F] =
    NettyCatsServer(Vector.empty, options, config)

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
