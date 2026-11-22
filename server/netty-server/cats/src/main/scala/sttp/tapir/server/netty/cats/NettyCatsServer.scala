package sttp.tapir.server.netty.cats

import cats.effect.std.Dispatcher
import cats.effect.{Async, IO, Outcome, Resource}
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
import sttp.tapir.server.netty.internal.{NettyBootstrap, NettyServerHandler, NettyShutdown}
import sttp.tapir.server.netty.{NettyConfig, NettyResponse, Route}

import java.net.{InetSocketAddress, SocketAddress}
import java.nio.file.{Path, Paths}
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.Future

case class NettyCatsServer[F[_]: Async](
    serverEndpoints: Vector[ServerEndpoint[Fs2Streams[F] & WebSockets, F]],
    otherRoutes: Vector[Route[F]],
    options: NettyCatsServerOptions[F],
    config: NettyConfig
) {
  def addEndpoint(se: ServerEndpoint[Fs2Streams[F] & WebSockets, F]): NettyCatsServer[F] = addEndpoints(List(se))
  def addEndpoints(ses: List[ServerEndpoint[Fs2Streams[F] & WebSockets, F]]): NettyCatsServer[F] =
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

  private def startUsingSocketOverride[SA <: SocketAddress](socketOverride: Option[SA]): F[(SA, () => F[Unit])] =
    // uncancelable until the cleanup is in place; only waiting for the bind can be cancelled
    Async[F].uncancelable { poll =>
      // deferred, so that each run allocates, also for Async instances which don't call the body lazily
      Async[F].defer {
        val eventLoopGroup = config.eventLoopConfig.initEventLoopGroup()
        implicit val monadError: MonadError[F] = new CatsMonadError[F]()
        val endpointRoute = serverEndpoints match {
          case Vector() => Vector.empty
          case _        => Vector(NettyCatsServerInterpreter(options).toRoute(serverEndpoints.toList))
        }
        val allRoutes = endpointRoute ++ otherRoutes
        val route = allRoutes match {
          case Vector()  => Route.empty
          case Vector(r) => r
          case many      => Route.combine(many)
        }
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

        val bound = poll(nettyChannelFutureToScala(channelFuture)).flatMap { ch =>
          // repeated calls return the first call's result, as the executors are then shut down; uncancelable, as a cancelled first call
          // would make `memoize` run the stop again
          Async[F]
            .memoize(stop(ch, eventLoopGroup, channelGroup, eventExecutor, isShuttingDown))
            .map(stopped => (ch.localAddress().asInstanceOf[SA], () => Async[F].uncancelable(_ => stopped)))
        }
        Async[F].guaranteeCase(bound) {
          case Outcome.Succeeded(_) => Async[F].unit
          case _                    =>
            // if cancelled, the bind might still complete
            Async[F].defer(nettyFutureToScala(channelFuture.channel().close())).attempt >> shutdownExecutors(eventLoopGroup, eventExecutor)
        }
      }
    }

  private def stop(
      serverChannel: Channel,
      eventLoopGroup: EventLoopGroup,
      channelGroup: ChannelGroup,
      eventExecutor: DefaultEventExecutor,
      isShuttingDown: AtomicBoolean
  ): F[Unit] = {
    val drain = Async[F].defer(
      nettyFutureToScala(NettyShutdown.drain(serverChannel, channelGroup, eventExecutor, isShuttingDown, config.gracefulShutdownTimeout))
    )
    Async[F].guarantee(drain.void, shutdownExecutors(eventLoopGroup, eventExecutor))
  }

  private def shutdownExecutors(eventLoopGroup: EventLoopGroup, eventExecutor: DefaultEventExecutor): F[Unit] =
    Async[F].defer(
      nettyFutureToScala(
        NettyShutdown.shutdownExecutors(eventLoopGroup, config.shutdownEventLoopGroupOnClose, eventExecutor)
      ).void
    )
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
