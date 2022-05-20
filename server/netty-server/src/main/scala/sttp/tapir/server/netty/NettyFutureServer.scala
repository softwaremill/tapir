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

case class NettyFutureServer[S <: NettyServerType](routes: Vector[FutureRoute], options: NettyFutureServerOptions)(implicit
    ec: ExecutionContext
) {
  def addEndpoint(se: ServerEndpoint[Any, Future]): NettyFutureServer[S] = addEndpoints(List(se))
  def addEndpoint(se: ServerEndpoint[Any, Future], overrideOptions: NettyFutureServerOptions): NettyFutureServer[S] =
    addEndpoints(List(se), overrideOptions)
  def addEndpoints(ses: List[ServerEndpoint[Any, Future]]): NettyFutureServer[S] = addRoute(
    NettyFutureServerInterpreter(options).toRoute(ses)
  )
  def addEndpoints(ses: List[ServerEndpoint[Any, Future]], overrideOptions: NettyFutureServerOptions): NettyFutureServer[S] =
    addRoute(
      NettyFutureServerInterpreter(overrideOptions).toRoute(ses)
    )

  def addRoute(r: FutureRoute): NettyFutureServer[S] = copy(routes = routes :+ r)
  def addRoutes(r: Iterable[FutureRoute]): NettyFutureServer[S] = copy(routes = routes ++ r)

  def options(o: NettyFutureServerOptions): NettyFutureServer[S] = copy(options = o)

  def host(hostname: String)(implicit isTCP: S =:= NettyServerType.TCP): NettyFutureServer[S] = {
    val nettyOptions = options.nettyOptions.host(hostname)

    options(options.nettyOptions(nettyOptions))
  }

  def port(p: Int)(implicit isTCP: S =:= NettyServerType.TCP): NettyFutureServer[S] = {
    val nettyOptions = options.nettyOptions.port(p)

    options(options.nettyOptions(nettyOptions))
  }

  def path(path: Path)(implicit isDomainSocket: S =:= NettyServerType.DomainSocket): NettyFutureServer[S] = {
    val nettyOptions = options.nettyOptions.path(path)

    options(options.nettyOptions(nettyOptions))
  }

  def start(): Future[NettyFutureServerBinding[S]] = {
    val eventLoopGroup = options.nettyOptions.eventLoopConfig.initEventLoopGroup()
    implicit val monadError: MonadError[Future] = new FutureMonad()
    val route = Route.combine(routes)

    val channelFuture = NettyBootstrap(
      options.nettyOptions,
      new NettyServerHandler(route, (f: Future[Unit]) => f),
      eventLoopGroup
    )

    nettyChannelFutureToScala(channelFuture).map(ch =>
      NettyFutureServerBinding(
        ch.localAddress(),
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
  def apply(
      serverOptions: NettyFutureServerOptions = NettyFutureServerOptions.defaultTcp
  )(implicit ec: ExecutionContext): NettyFutureServer[NettyServerType.TCP] = {
    NettyFutureServer[NettyServerType.TCP](Vector.empty, serverOptions)
  }

  def tcp(implicit ec: ExecutionContext): NettyFutureServer[NettyServerType.TCP] = {
    new NettyFutureServer[NettyServerType.TCP](Vector.empty, NettyFutureServerOptions.defaultTcp)
  }

  def domainSocket(implicit ec: ExecutionContext): NettyFutureServer[NettyServerType.DomainSocket] = {
    new NettyFutureServer[NettyServerType.DomainSocket](Vector.empty, NettyFutureServerOptions.defaultDomainSocket)
  }
}

case class NettyFutureServerBinding[S <: NettyServerType](localSocket: SocketAddress, stop: () => Future[Unit]) {
  def hostName(implicit isTCP: S =:= NettyServerType.TCP): String = {
    localSocket.asInstanceOf[InetSocketAddress].getHostName
  }

  def port(implicit isTCP: S =:= NettyServerType.TCP): Int = {
    localSocket.asInstanceOf[InetSocketAddress].getPort
  }

  def path(implicit isDomainSocket: S =:= NettyServerType.DomainSocket): String = {
    localSocket.asInstanceOf[DomainSocketAddress].path()
  }
}
