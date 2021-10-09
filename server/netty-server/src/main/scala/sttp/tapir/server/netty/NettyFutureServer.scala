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

case class NettyFutureServer[S <: SocketAddress](routes: Vector[FutureRoute], options: NettyFutureServerOptions[S])(implicit
    ec: ExecutionContext
) {
  def addEndpoint(se: ServerEndpoint[Any, Future]): NettyFutureServer[S] = addEndpoints(List(se))
  def addEndpoint(se: ServerEndpoint[Any, Future], overrideOptions: NettyFutureServerOptions[S]): NettyFutureServer[S] =
    addEndpoints(List(se), overrideOptions)
  def addEndpoints(ses: List[ServerEndpoint[Any, Future]]): NettyFutureServer[S] = addRoute(
    NettyFutureServerInterpreter(options).toRoute(ses)
  )
  def addEndpoints(ses: List[ServerEndpoint[Any, Future]], overrideOptions: NettyFutureServerOptions[S]): NettyFutureServer[S] =
    addRoute(
      NettyFutureServerInterpreter(overrideOptions).toRoute(ses)
    )

  def addRoute(r: FutureRoute): NettyFutureServer[S] = copy(routes = routes :+ r)
  def addRoutes(r: Iterable[FutureRoute]): NettyFutureServer[S] = copy(routes = routes ++ r)

  def options(o: NettyFutureServerOptions[S]): NettyFutureServer[S] = copy(options = o)
  def host(s: String): NettyFutureServer[S] = copy(options = options.host(s))
  def port(p: Int): NettyFutureServer[S] = copy(options = options.port(p))

  def start(): Future[NettyFutureServerBinding[S]] = {
//    val eventLoopGroup = options.nettyOptions.eventLoopGroup()
    val eventLoopGroup = options.nettyOptions.eventLoopConfig.builder()
    implicit val monadError: MonadError[Future] = new FutureMonad()
    val route = Route.combine(routes)

    val channelFuture = NettyBootstrap(
      options.nettyOptions,
      new NettyServerHandler(route, (f: Future[Unit]) => f),
      eventLoopGroup,
      options.nettyOptions.eventLoopConfig.serverChannel,
      options.nettyOptions.socketAddress
//      options.host,
//      options.port
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

class TcpFutureServerBuilder(private var nettyOptions: TcpOptionsBuilder)(implicit ec: ExecutionContext)
    extends NettyFutureServer[InetSocketAddress](Vector.empty) {
  def eventLoopGroup(group: EventLoopGroup) = {
    nettyOptions = nettyOptions.eventLoopGroup(group)
  }
  def port(port: Int) = {
    nettyOptions = nettyOptions.port(port)
    this
  }
  def host(host: String) = {
    nettyOptions = nettyOptions.host(host)
    this
  }

  override def options: NettyFutureServerOptions[InetSocketAddress] = NettyFutureServerOptions.default.nettyOptions(nettyOptions.build)
}

class DomainSocketFutureServerBuilder(private var nettyOptions: DomainSocketOptionsBuilder)(implicit ec: ExecutionContext)
    extends NettyFutureServer[DomainSocketAddress](Vector.empty) {
  def path(path: Path) = {
    nettyOptions = nettyOptions.path(path)
    this
  }
  def eventLoopGroup(group: EventLoopGroup) = {
    nettyOptions = nettyOptions.eventLoopGroup(group)
  }

  override def options: NettyFutureServerOptions[DomainSocketAddress] = NettyFutureServerOptions.default.nettyOptions(nettyOptions.build)
}

object NettyFutureServer {
  def apply(
      serverOptions: NettyFutureServerOptions[InetSocketAddress] = NettyFutureServerOptions.default
  )(implicit ec: ExecutionContext): NettyFutureServer[InetSocketAddress] = {
    new NettyFutureServer[InetSocketAddress](Vector.empty) {
      override def options: NettyFutureServerOptions[InetSocketAddress] = serverOptions
    }
  }

  def tcp(implicit ec: ExecutionContext) = {
    new TcpFutureServerBuilder(NettyOptionsBuilder.make().tcp())
  }

  def unixDomainSocket(implicit ec: ExecutionContext) = {
    new DomainSocketFutureServerBuilder(NettyOptionsBuilder.make().domainSocket())
  }
}

case class NettyFutureServerBinding[S <: SocketAddress](localSocket: S, stop: () => Future[Unit])
