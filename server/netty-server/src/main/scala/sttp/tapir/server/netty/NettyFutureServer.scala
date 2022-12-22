package sttp.tapir.server.netty

import io.netty.channel._
import io.netty.channel.unix.DomainSocketAddress
import sttp.monad.{FutureMonad, MonadError}
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.internal.FutureUtil._
import sttp.tapir.server.netty.internal.{NettyBootstrap, NettyServerHandler}

import java.net.{InetSocketAddress, SocketAddress}
import java.nio.file.Path
import scala.concurrent.{ExecutionContext, Future}

case class NettyFutureServer[SA <: SocketAddress](routes: Vector[FutureRoute], options: NettyFutureServerOptions[SA])(implicit
    ec: ExecutionContext
) {
  def addEndpoint(se: ServerEndpoint[Any, Future]): NettyFutureServer[SA] = addEndpoints(List(se))
  def addEndpoint(se: ServerEndpoint[Any, Future], overrideOptions: NettyFutureServerOptions[SA]): NettyFutureServer[SA] =
    addEndpoints(List(se), overrideOptions)
  def addEndpoints(ses: List[ServerEndpoint[Any, Future]]): NettyFutureServer[SA] = addRoute(
    NettyFutureServerInterpreter(options).toRoute(ses)
  )
  def addEndpoints(ses: List[ServerEndpoint[Any, Future]], overrideOptions: NettyFutureServerOptions[SA]): NettyFutureServer[SA] =
    addRoute(NettyFutureServerInterpreter(overrideOptions).toRoute(ses))

  def addRoute(r: FutureRoute): NettyFutureServer[SA] = copy(routes = routes :+ r)
  def addRoutes(r: Iterable[FutureRoute]): NettyFutureServer[SA] = copy(routes = routes ++ r)

  def options[SA2 <: SocketAddress](o: NettyFutureServerOptions[SA2]): NettyFutureServer[SA2] = copy(options = o)

  def host(hostname: String)(implicit isTCP: SA =:= InetSocketAddress): NettyFutureServer[InetSocketAddress] = {
    val nettyOptions = options.nettyOptions.host(hostname)
    options(options.nettyOptions(nettyOptions))
  }

  def port(p: Int)(implicit isTCP: SA =:= InetSocketAddress): NettyFutureServer[InetSocketAddress] = {
    val nettyOptions = options.nettyOptions.port(p)
    options(options.nettyOptions(nettyOptions))
  }

  def domainSocketPath(path: Path)(implicit isDomainSocket: SA =:= DomainSocketAddress): NettyFutureServer[DomainSocketAddress] = {
    val nettyOptions = options.nettyOptions.domainSocketPath(path)
    options(options.nettyOptions(nettyOptions))
  }

  def start(): Future[NettyFutureServerBinding[SA]] = {
    val eventLoopGroup = options.nettyOptions.eventLoopConfig.initEventLoopGroup()
    implicit val monadError: MonadError[Future] = new FutureMonad()
    val route = Route.combine(routes)

    val channelFuture = NettyBootstrap(
      options.nettyOptions,
      new NettyServerHandler(route, (f: () => Future[Unit]) => f()),
      eventLoopGroup
    )

    nettyChannelFutureToScala(channelFuture).map(ch =>
      NettyFutureServerBinding(
        ch.localAddress().asInstanceOf[SA],
        () => stop(ch, eventLoopGroup)
      )
    )
  }

  private def stop(ch: Channel, eventLoopGroup: EventLoopGroup): Future[Unit] = {
    nettyFutureToScala(ch.close()).flatMap { _ =>
      if (options.nettyOptions.shutdownEventLoopGroupOnClose) {
        nettyFutureToScala(eventLoopGroup.shutdownGracefully()).map(_ => ())
      } else Future.successful(())
    }
  }
}

object NettyFutureServer {
  def apply()(implicit ec: ExecutionContext): NettyFutureServer[InetSocketAddress] =
    NettyFutureServer(Vector.empty, NettyFutureServerOptions.default)

  def apply[SA <: SocketAddress](serverOptions: NettyFutureServerOptions[SA])(implicit ec: ExecutionContext): NettyFutureServer[SA] =
    NettyFutureServer[SA](Vector.empty, serverOptions)
}

case class NettyFutureServerBinding[SA <: SocketAddress](localSocket: SA, stop: () => Future[Unit]) {
  def hostName(implicit isTCP: SA =:= InetSocketAddress): String = isTCP(localSocket).getHostName
  def port(implicit isTCP: SA =:= InetSocketAddress): Int = isTCP(localSocket).getPort
  def path(implicit isDomainSocket: SA =:= DomainSocketAddress): String = isDomainSocket(localSocket).path()
}
