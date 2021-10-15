package sttp.tapir.server.netty

import io.netty.channel._
import io.netty.channel.unix.DomainSocketAddress
import sttp.monad.{FutureMonad, MonadError}
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.NettyOptionsBuilder.{DomainSocketOptionsBuilder, TcpOptionsBuilder}
import sttp.tapir.server.netty.internal.FutureUtil._
import sttp.tapir.server.netty.internal.{NettyBootstrap, NettyServerHandler}

import java.net.{InetSocketAddress, SocketAddress}
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

object NettyFutureServer {
  def apply(serverOptions: NettyFutureServerOptions = NettyFutureServerOptions.default)(implicit ec: ExecutionContext): NettyFutureServer[InetSocketAddress] =
    NettyFutureServer(Vector.empty, serverOptions)

  def tcp(f: TcpOptionsBuilder => TcpOptionsBuilder = identity)(implicit ec: ExecutionContext): NettyFutureServer[InetSocketAddress] = {
    NettyFutureServer(Vector.empty, NettyFutureServerOptions.default.copy(nettyOptions = f(NettyOptionsBuilder.make().tcp()).build))
  }

  def unixDomainSocket(f: DomainSocketOptionsBuilder => DomainSocketOptionsBuilder = identity)(implicit ec: ExecutionContext): NettyFutureServer[DomainSocketAddress] = {
    NettyFutureServer(Vector.empty, NettyFutureServerOptions.default.copy(nettyOptions = f(NettyOptionsBuilder.make().domainSocket()).build))
  }
}

case class NettyServerOptionsBuilder(options: NettyFutureServerOptions) {

}

case class NettyFutureServerBinding[S <: SocketAddress](localSocket: S, stop: () => Future[Unit])
