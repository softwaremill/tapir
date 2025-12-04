package sttp.tapir.server.netty.zio

import io.netty.channel._
import io.netty.channel.group.{ChannelGroup, DefaultChannelGroup}
import io.netty.channel.unix.DomainSocketAddress
import io.netty.util.concurrent.DefaultEventExecutor
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.model.ServerResponse
import sttp.tapir.server.netty.internal.{NettyBootstrap, NettyServerHandler}
import sttp.tapir.server.netty.zio.internal.ZioUtil.{nettyChannelFutureToScala, nettyFutureToScala}
import sttp.tapir.server.netty.{NettyConfig, NettyResponse, Route}
import sttp.tapir.ztapir.{RIOMonadError, ZServerEndpoint}
import zio.{RIO, Task, Unsafe, ZIO, durationInt}

import java.net.{InetSocketAddress, SocketAddress}
import java.nio.file.{Path, Paths}
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.ExecutionContext.Implicits
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

case class NettyZioServer[R](
    serverEndpoints: Vector[ServerEndpoint[ZioStreams, RIO[R, *]]],
    otherRoutes: Vector[RIO[R, Route[RIO[R, *]]]],
    options: NettyZioServerOptions[R],
    config: NettyConfig
) {
  def addEndpoint(se: ZServerEndpoint[R, ZioStreams]): NettyZioServer[R] = addEndpoints(List(se))
  def addEndpoints(ses: List[ServerEndpoint[ZioStreams, RIO[R, *]]]): NettyZioServer[R] = copy(serverEndpoints = serverEndpoints ++ ses)

  /** Adds a custom route to the server. When a request is received, it is first processed by routes generated from the defined endpoints
    * (see [[addEndpoint]] and [[addEndpoints]] for the primary methods of defining server behavior). If none of these endpoints match the
    * request, and the [[RejectHandler]] is configured to allow fallback handling (see below), the request will then be processed by the
    * custom routes added using this method, in the order they were added.
    *
    * By default, the [[NettyZioServerOptions]] are configured to return a `404` response when no endpoints match a request. This behavior
    * is controlled by the [[RejectHandler]]. If you intend to handle unmatched requests using custom routes, ensure that the
    * [[RejectHandler]] is configured appropriately to allow such fallback handling.
    */
  def addRoute(r: Route[RIO[R, *]]): NettyZioServer[R] = addRoute(ZIO.succeed(r))

  /** Adds custom routes to the server. When a request is received, it is first processed by routes generated from the defined endpoints
    * (see [[addEndpoint]] and [[addEndpoints]] for the primary methods of defining server behavior). If none of these endpoints match the
    * request, and the [[RejectHandler]] is configured to allow fallback handling (see below), the request will then be processed by the
    * custom routes added using this method, in the order they were added.
    *
    * By default, the [[NettyZioServerOptions]] are configured to return a `404` response when no endpoints match a request. This behavior
    * is controlled by the [[RejectHandler]]. If you intend to handle unmatched requests using custom routes, ensure that the
    * [[RejectHandler]] is configured appropriately to allow such fallback handling.
    */
  def addRoute(r: RIO[R, Route[RIO[R, *]]]): NettyZioServer[R] = copy(otherRoutes = otherRoutes :+ r)

  /** Adds custom routes to the server. When a request is received, it is first processed by routes generated from the defined endpoints
    * (see [[addEndpoint]] and [[addEndpoints]] for the primary methods of defining server behavior). If none of these endpoints match the
    * request, and the [[RejectHandler]] is configured to allow fallback handling (see below), the request will then be processed by the
    * custom routes added using this method, in the order they were added.
    *
    * By default, the [[NettyZioServerOptions]] are configured to return a `404` response when no endpoints match a request. This behavior
    * is controlled by the [[RejectHandler]]. If you intend to handle unmatched requests using custom routes, ensure that the
    * [[RejectHandler]] is configured appropriately to allow such fallback handling.
    */
  def addRoutes(r: Iterable[RIO[R, Route[RIO[R, *]]]]): NettyZioServer[R] = copy(otherRoutes = otherRoutes ++ r)

  def options(o: NettyZioServerOptions[R]): NettyZioServer[R] = copy(options = o)

  def config(c: NettyConfig): NettyZioServer[R] = copy(config = c)
  def modifyConfig(f: NettyConfig => NettyConfig): NettyZioServer[R] = config(f(config))

  def host(h: String): NettyZioServer[R] = modifyConfig(_.host(h))

  def port(p: Int): NettyZioServer[R] = modifyConfig(_.port(p))

  def start(): RIO[R, NettyZioServerBinding[R]] =
    startUsingSocketOverride[InetSocketAddress](None).map { case (socket, stop) =>
      NettyZioServerBinding(socket, stop)
    }

  def startUsingDomainSocket(path: Option[Path] = None): RIO[R, NettyZioDomainSocketBinding[R]] =
    startUsingDomainSocket(path.getOrElse(Paths.get(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString)))

  def startUsingDomainSocket(path: Path): RIO[R, NettyZioDomainSocketBinding[R]] =
    startUsingSocketOverride(Some(new DomainSocketAddress(path.toFile))).map { case (socket, stop) =>
      NettyZioDomainSocketBinding(socket, stop)
    }

  private def unsafeRunAsync(
      runtime: zio.Runtime[R]
  )(block: () => RIO[R, ServerResponse[NettyResponse]]): (Future[ServerResponse[NettyResponse]], () => Future[Unit]) = {
    val cancelable = Unsafe.unsafe(implicit u =>
      runtime.unsafe.runToFuture(
        block()
      )
    )
    (cancelable, () => cancelable.cancel().map(_ => ())(Implicits.global))
  }

  private def startUsingSocketOverride[SA <: SocketAddress](socketOverride: Option[SA]): RIO[R, (SA, () => RIO[R, Unit])] = for {
    runtime <- ZIO.runtime[R]
    endpointRoute <- serverEndpoints match {
      case Vector() => ZIO.succeed(Vector.empty[Route[RIO[R, *]]])
      case _        => NettyZioServerInterpreter(options).toRoute(serverEndpoints.toList).map(Vector(_))
    }
    routes <- ZIO.foreach(otherRoutes)(identity)
    eventLoopGroup = config.eventLoopConfig.initEventLoopGroup()
    eventExecutor = new DefaultEventExecutor()
    channelGroup = new DefaultChannelGroup(eventExecutor) // thread safe
    isShuttingDown = new AtomicBoolean(false)
    channelFuture = {
      implicit val monadError: RIOMonadError[R] = new RIOMonadError[R]

      val allRoutes = endpointRoute ++ routes
      val route: Route[RIO[R, *]] = allRoutes match {
        case Vector()  => Route.empty
        case Vector(r) => r
        case many      => Route.combine(many)
      }

      NettyBootstrap[RIO[R, *]](
        config,
        new NettyServerHandler[RIO[R, *]](
          route,
          unsafeRunAsync(runtime),
          channelGroup,
          isShuttingDown,
          config
        ),
        eventLoopGroup,
        socketOverride
      )
    }
    binding <- nettyChannelFutureToScala(channelFuture)
      .map(ch =>
        (
          ch.localAddress().asInstanceOf[SA],
          () => stop(ch, eventLoopGroup, channelGroup, eventExecutor, isShuttingDown, config.gracefulShutdownTimeout)
        )
      )
      .catchAll { e =>
        stopRecovering(eventLoopGroup, channelGroup, eventExecutor, isShuttingDown, config.gracefulShutdownTimeout) *>
          ZIO.fail(e)
      }
  } yield binding

  private def waitForClosedChannels(
      channelGroup: ChannelGroup,
      startNanos: Long,
      gracefulShutdownTimeoutNanos: Option[Long]
  ): Task[Unit] =
    if (!channelGroup.isEmpty && gracefulShutdownTimeoutNanos.exists(_ >= System.nanoTime() - startNanos)) {
      ZIO.sleep(100.millis) *>
        waitForClosedChannels(channelGroup, startNanos, gracefulShutdownTimeoutNanos)
    } else {
      ZIO.attempt(channelGroup.close()).unit
    }

  private def stop(
      ch: Channel,
      eventLoopGroup: EventLoopGroup,
      channelGroup: ChannelGroup,
      eventExecutor: DefaultEventExecutor,
      isShuttingDown: AtomicBoolean,
      gracefulShutdownTimeout: Option[FiniteDuration]
  ): RIO[R, Unit] = {
    stopChannelGroup(channelGroup, isShuttingDown, gracefulShutdownTimeout) *>
      ZIO.suspend {
        nettyFutureToScala(ch.close()).flatMap { _ =>
          stopEventLoopGroup(eventLoopGroup, eventExecutor)
        }
      }
  }

  private def stopRecovering(
      eventLoopGroup: EventLoopGroup,
      channelGroup: ChannelGroup,
      eventExecutor: DefaultEventExecutor,
      isShuttingDown: AtomicBoolean,
      gracefulShutdownTimeout: Option[FiniteDuration]
  ): RIO[R, Unit] = {
    stopChannelGroup(channelGroup, isShuttingDown, gracefulShutdownTimeout) *>
      stopEventLoopGroup(eventLoopGroup, eventExecutor)
  }

  private def stopChannelGroup(
      channelGroup: ChannelGroup,
      isShuttingDown: AtomicBoolean,
      gracefulShutdownTimeout: Option[FiniteDuration]
  ) = {
    ZIO.attempt(isShuttingDown.set(true)) *>
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
    } else ZIO.succeed(())
  }
}

object NettyZioServer {
  def apply[R](): NettyZioServer[R] = NettyZioServer(Vector.empty, Vector.empty, NettyZioServerOptions.default[R], NettyConfig.default)
  def apply[R](options: NettyZioServerOptions[R]): NettyZioServer[R] =
    NettyZioServer(Vector.empty, Vector.empty, options, NettyConfig.default)
  def apply[R](config: NettyConfig): NettyZioServer[R] =
    NettyZioServer(Vector.empty, Vector.empty, NettyZioServerOptions.default[R], config)
  def apply[R](options: NettyZioServerOptions[R], config: NettyConfig): NettyZioServer[R] =
    NettyZioServer(Vector.empty, Vector.empty, options, config)
}

case class NettyZioServerBinding[R](localSocket: InetSocketAddress, stop: () => RIO[R, Unit]) {
  def hostName: String = localSocket.getHostName
  def port: Int = localSocket.getPort
}

case class NettyZioDomainSocketBinding[R](localSocket: DomainSocketAddress, stop: () => RIO[R, Unit]) {
  def path: String = localSocket.path()
}
