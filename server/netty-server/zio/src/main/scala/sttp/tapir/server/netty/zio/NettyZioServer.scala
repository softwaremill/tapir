package sttp.tapir.server.netty.zio

import io.netty.channel._
import io.netty.channel.unix.DomainSocketAddress
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.model.ServerResponse
import sttp.tapir.server.netty.internal.{NettyBootstrap, NettyServerHandler}
import sttp.tapir.server.netty.zio.internal.ZioUtil.{nettyChannelFutureToScala, nettyFutureToScala}
import sttp.tapir.server.netty.{NettyConfig, NettyResponse, Route}
import sttp.tapir.ztapir.{RIOMonadError, ZServerEndpoint}
import zio.{RIO, Unsafe, ZIO}

import java.net.{InetSocketAddress, SocketAddress}
import java.nio.file.{Path, Paths}
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits
import scala.concurrent.Future

case class NettyZioServer[R](routes: Vector[RIO[R, Route[RIO[R, *]]]], options: NettyZioServerOptions[R], config: NettyConfig) {
  def addEndpoint(se: ZServerEndpoint[R, ZioStreams]): NettyZioServer[R] = addEndpoints(List(se))
  def addEndpoint(se: ZServerEndpoint[R, ZioStreams], overrideOptions: NettyZioServerOptions[R]): NettyZioServer[R] =
    addEndpoints(List(se), overrideOptions)
  def addEndpoints(ses: List[ServerEndpoint[ZioStreams, RIO[R, *]]]): NettyZioServer[R] = addRoute(
    NettyZioServerInterpreter[R](options).toRoute(ses)
  )
  def addEndpoints(
      ses: List[ZServerEndpoint[R, ZioStreams]],
      overrideOptions: NettyZioServerOptions[R]
  ): NettyZioServer[R] = addRoute(
    NettyZioServerInterpreter[R](overrideOptions).toRoute(ses)
  )

  def addRoute(r: Route[RIO[R, *]]): NettyZioServer[R] = addRoute(ZIO.succeed(r))

  def addRoute(r: RIO[R, Route[RIO[R, *]]]): NettyZioServer[R] = copy(routes = routes :+ r)
  def addRoutes(r: Iterable[RIO[R, Route[RIO[R, *]]]]): NettyZioServer[R] = copy(routes = routes ++ r)

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
    routes <- ZIO.foreach(routes)(identity)
    eventLoopGroup = config.eventLoopConfig.initEventLoopGroup()
    channelFuture = {
      implicit val monadError: RIOMonadError[R] = new RIOMonadError[R]
      val route: Route[RIO[R, *]] = Route.combine(routes)

      NettyBootstrap[RIO[R, *]](
        config,
        new NettyServerHandler[RIO[R, *]](
          route,
          unsafeRunAsync(runtime),
          config.maxContentLength
        ),
        eventLoopGroup,
        socketOverride
      )
    }
    binding <- nettyChannelFutureToScala(channelFuture).map(ch =>
      (
        ch.localAddress().asInstanceOf[SA],
        () => stop(ch, eventLoopGroup)
      )
    )
  } yield binding

  private def stop(ch: Channel, eventLoopGroup: EventLoopGroup): RIO[R, Unit] = {
    ZIO.suspend {
      nettyFutureToScala(ch.close()).flatMap { _ =>
        if (config.shutdownEventLoopGroupOnClose) {
          nettyFutureToScala(eventLoopGroup.shutdownGracefully()).map(_ => ())
        } else ZIO.succeed(())
      }
    }
  }
}

object NettyZioServer {
  def apply[R](): NettyZioServer[R] = NettyZioServer(Vector.empty, NettyZioServerOptions.default[R], NettyConfig.defaultWithStreaming)
  def apply[R](options: NettyZioServerOptions[R]): NettyZioServer[R] =
    NettyZioServer(Vector.empty, options, NettyConfig.defaultWithStreaming)
  def apply[R](config: NettyConfig): NettyZioServer[R] = NettyZioServer(Vector.empty, NettyZioServerOptions.default[R], config)
  def apply[R](options: NettyZioServerOptions[R], config: NettyConfig): NettyZioServer[R] = NettyZioServer(Vector.empty, options, config)
}

case class NettyZioServerBinding[R](localSocket: InetSocketAddress, stop: () => RIO[R, Unit]) {
  def hostName: String = localSocket.getHostName
  def port: Int = localSocket.getPort
}

case class NettyZioDomainSocketBinding[R](localSocket: DomainSocketAddress, stop: () => RIO[R, Unit]) {
  def path: String = localSocket.path()
}
