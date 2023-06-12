package sttp.tapir.server.netty.zio

import io.netty.channel._
import io.netty.channel.unix.DomainSocketAddress
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.Route
import sttp.tapir.server.netty.internal.{NettyBootstrap, NettyServerHandler}
import sttp.tapir.server.netty.zio.internal.ZioUtil.{nettyChannelFutureToScala, nettyFutureToScala}
import sttp.tapir.ztapir.{RIOMonadError, ZServerEndpoint}
import zio.{RIO, Unsafe, ZIO}

import java.net.{InetSocketAddress, SocketAddress}
import java.nio.file.Path

case class NettyZioServer[R, SA <: SocketAddress](routes: Vector[RIO[R, Route[RIO[R, *]]]], options: NettyZioServerOptions[R, SA]) {
  def addEndpoint(se: ZServerEndpoint[R, Any]): NettyZioServer[R, SA] = addEndpoints(List(se))
  def addEndpoint(
      se: ZServerEndpoint[R, Any],
      overrideOptions: NettyZioServerOptions[R, SA]
  ): NettyZioServer[R, SA] =
    addEndpoints(List(se), overrideOptions)
  def addEndpoints(ses: List[ServerEndpoint[Any, RIO[R, *]]]): NettyZioServer[R, SA] = addRoute(
    NettyZioServerInterpreter[R](options).toRoute(ses)
  )
  def addEndpoints(
      ses: List[ZServerEndpoint[R, Any]],
      overrideOptions: NettyZioServerOptions[R, SA]
  ): NettyZioServer[R, SA] = addRoute(
    NettyZioServerInterpreter[R](overrideOptions).toRoute(ses)
  )

  def addRoute(r: Route[RIO[R, *]]): NettyZioServer[R, SA] = addRoute(ZIO.succeed(r))

  def addRoute(r: RIO[R, Route[RIO[R, *]]]): NettyZioServer[R, SA] = copy(routes = routes :+ r)
  def addRoutes(r: Iterable[RIO[R, Route[RIO[R, *]]]]): NettyZioServer[R, SA] = copy(routes = routes ++ r)

  def options[SA2 <: SocketAddress](o: NettyZioServerOptions[R, SA2]): NettyZioServer[R, SA2] = copy(options = o)

  def host(hostname: String)(implicit isTCP: SA =:= InetSocketAddress): NettyZioServer[R, InetSocketAddress] = {
    val nettyOptions = options.nettyOptions.host(hostname)
    options(options.nettyOptions(nettyOptions))
  }

  def port(p: Int)(implicit isTCP: SA =:= InetSocketAddress): NettyZioServer[R, InetSocketAddress] = {
    val nettyOptions = options.nettyOptions.port(p)
    options(options.nettyOptions(nettyOptions))
  }

  def domainSocketPath(path: Path)(implicit isDomainSocket: SA =:= DomainSocketAddress): NettyZioServer[R, DomainSocketAddress] = {
    val nettyOptions = options.nettyOptions.domainSocketPath(path)
    options(options.nettyOptions(nettyOptions))
  }

  def start(): RIO[R, NettyZioServerBinding[R, SA]] = for {
    runtime <- ZIO.runtime[R]
    routes <- ZIO.foreach(routes)(identity)
    eventLoopGroup = options.nettyOptions.eventLoopConfig.initEventLoopGroup()
    channelFuture = {
      implicit val monadError: RIOMonadError[R] = new RIOMonadError[R]
      val route: Route[RIO[R, *]] = Route.combine(routes)

      NettyBootstrap[RIO[R, *]](
        options.nettyOptions,
        new NettyServerHandler[RIO[R, *]](
          route,
          (f: () => RIO[R, Unit]) => Unsafe.unsafe(implicit u => runtime.unsafe.runToFuture(f()))
        ),
        eventLoopGroup
      )
    }
    binding <- nettyChannelFutureToScala(channelFuture).map(ch =>
      NettyZioServerBinding(
        ch.localAddress().asInstanceOf[SA],
        () => stop(ch, eventLoopGroup)
      )
    )
  } yield binding

  private def stop(ch: Channel, eventLoopGroup: EventLoopGroup): RIO[R, Unit] = {
    ZIO.suspend {
      nettyFutureToScala(ch.close()).flatMap { _ =>
        if (options.nettyOptions.shutdownEventLoopGroupOnClose) {
          nettyFutureToScala(eventLoopGroup.shutdownGracefully()).map(_ => ())
        } else ZIO.succeed(())
      }
    }
  }
}

object NettyZioServer {
  def apply[R](): NettyZioServer[R, InetSocketAddress] =
    apply(NettyZioServerOptions.default[R])

  def apply[R, SA <: SocketAddress](options: NettyZioServerOptions[R, SA]): NettyZioServer[R, SA] =
    NettyZioServer(Vector.empty, options)
}

case class NettyZioServerBinding[R, SA <: SocketAddress](localSocket: SA, stop: () => RIO[R, Unit]) {
  def hostName(implicit isTCP: SA =:= InetSocketAddress): String = isTCP(localSocket).getHostName
  def port(implicit isTCP: SA =:= InetSocketAddress): Int = isTCP(localSocket).getPort
  def path(implicit isDomainSocket: SA =:= DomainSocketAddress): String = isDomainSocket(localSocket).path()
}
