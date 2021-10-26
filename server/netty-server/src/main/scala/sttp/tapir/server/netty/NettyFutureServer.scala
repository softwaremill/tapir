package sttp.tapir.server.netty

import io.netty.channel._
import io.netty.channel.unix.DomainSocketAddress
import sttp.monad.{FutureMonad, MonadError}
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.NettyOptionsBuilder.{DomainSocketOptionsBuilder, TcpOptionsBuilder}
import sttp.tapir.server.netty.internal.FutureUtil._
import sttp.tapir.server.netty.internal.{NettyBootstrap, NettyServerHandler}

import java.net.{InetSocketAddress, SocketAddress}
import java.nio.file.Path
import scala.concurrent.{ExecutionContext, Future}

case class NettyFutureServer[S <: SocketAddress](routes: Vector[FutureRoute], options: NettyFutureServerOptions)(implicit ec: ExecutionContext) {
  def addEndpoint(se: ServerEndpoint[_, _, _, Any, Future]): NettyFutureServer[S] = addEndpoints(List(se))
  def addEndpoint(se: ServerEndpoint[_, _, _, Any, Future], overrideOptions: NettyFutureServerOptions): NettyFutureServer[S] =
    addEndpoints(List(se), overrideOptions)
  def addEndpoints(ses: List[ServerEndpoint[_, _, _, Any, Future]]): NettyFutureServer[S] = addRoute(
    NettyFutureServerInterpreter(options).toRoute(ses)
  )
  def addEndpoints(ses: List[ServerEndpoint[_, _, _, Any, Future]], overrideOptions: NettyFutureServerOptions): NettyFutureServer[S] =
    addRoute(
      NettyFutureServerInterpreter(overrideOptions).toRoute(ses)
    )

  def addRoute(r: FutureRoute): NettyFutureServer[S] = copy(routes = routes :+ r)
  def addRoutes(r: Iterable[FutureRoute]): NettyFutureServer[S] = copy(routes = routes ++ r)

  def start(): Future[NettyFutureServerBinding[S]] = {
    val eventLoopGroup = options.nettyOptions.eventLoopConfig.builder()
    implicit val monadError: MonadError[Future] = new FutureMonad()
    val route = Route.combine(routes)

    val channelFuture = NettyBootstrap(
      options.nettyOptions,
      new NettyServerHandler(route, (f: Future[Unit]) => f),
      eventLoopGroup,
      options.nettyOptions.eventLoopConfig.serverChannel,
      options.nettyOptions.socketAddress
    )

    nettyChannelFutureToScala(channelFuture).map(ch =>
      NettyFutureServerBinding(
        ch.localAddress().asInstanceOf[S],
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

case class TcpFutureServerBuilder(nettyOptions: TcpOptionsBuilder)(implicit ec: ExecutionContext) {
  def port(port: Int) = copy(nettyOptions = nettyOptions.port(port))
  def host(host: String) = copy(nettyOptions = nettyOptions.host(host))

  def endpoints: NettyFutureServer[InetSocketAddress] = NettyFutureServer(Vector.empty, NettyFutureServerOptions.default.nettyOptions(nettyOptions.build))
}

case class DomainSocketFutureServerBuilder(nettyOptions: DomainSocketOptionsBuilder)(implicit ec: ExecutionContext) {
  def path(path: Path) = copy(nettyOptions = nettyOptions.path(path))

  def endpoints: NettyFutureServer[DomainSocketAddress] = NettyFutureServer(Vector.empty, NettyFutureServerOptions.default.nettyOptions(nettyOptions.build))

}

object NettyFutureServer {
  def apply(serverOptions: NettyFutureServerOptions = NettyFutureServerOptions.default)(implicit ec: ExecutionContext): NettyFutureServer[InetSocketAddress] =
    NettyFutureServer(Vector.empty, serverOptions)

  def tcp(implicit ec: ExecutionContext) = {
    TcpFutureServerBuilder(NettyOptionsBuilder.make().tcp())
  }

  def unixDomainSocket(implicit ec: ExecutionContext) = {
    DomainSocketFutureServerBuilder(NettyOptionsBuilder.make().domainSocket())
  }
}

case class NettyServerOptionsBuilder(options: NettyFutureServerOptions) {

}

case class NettyFutureServerBinding[S <: SocketAddress](localSocket: S, stop: () => Future[Unit])
