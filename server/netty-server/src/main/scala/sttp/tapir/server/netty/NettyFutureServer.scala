package sttp.tapir.server.netty

import io.netty.channel._
import sttp.monad.{FutureMonad, MonadError}
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.internal.FutureUtil._
import sttp.tapir.server.netty.internal.{NettyBootstrap, NettyServerHandler}

import java.net.InetSocketAddress
import scala.concurrent.{ExecutionContext, Future}

case class NettyFutureServer(routes: Vector[FutureRoute], options: NettyFutureServerOptions)(implicit ec: ExecutionContext) {
  def addEndpoint(se: ServerEndpoint[Any, Future]): NettyFutureServer = addEndpoints(List(se))
  def addEndpoint(se: ServerEndpoint[Any, Future], overrideOptions: NettyFutureServerOptions): NettyFutureServer =
    addEndpoints(List(se), overrideOptions)
  def addEndpoints(ses: List[ServerEndpoint[Any, Future]]): NettyFutureServer = addRoute(
    NettyFutureServerInterpreter(options).toRoute(ses)
  )
  def addEndpoints(ses: List[ServerEndpoint[Any, Future]], overrideOptions: NettyFutureServerOptions): NettyFutureServer =
    addRoute(
      NettyFutureServerInterpreter(overrideOptions).toRoute(ses)
    )

  def addRoute(r: FutureRoute): NettyFutureServer = copy(routes = routes :+ r)
  def addRoutes(r: Iterable[FutureRoute]): NettyFutureServer = copy(routes = routes ++ r)

  def options(o: NettyFutureServerOptions): NettyFutureServer = copy(options = o)
  def host(s: String): NettyFutureServer = copy(options = options.host(s))
  def port(p: Int): NettyFutureServer = copy(options = options.port(p))

  def start(): Future[NettyFutureServerBinding] = {
    val eventLoopGroup = options.nettyOptions.eventLoopGroup()
    implicit val monadError: MonadError[Future] = new FutureMonad()
    val route = Route.combine(routes)

    val channelFuture = NettyBootstrap(
      options.nettyOptions,
      new NettyServerHandler(route, (f: Future[Unit]) => f),
      eventLoopGroup,
      options.host,
      options.port
    )

    nettyChannelFutureToScala(channelFuture).map(ch =>
      NettyFutureServerBinding(
        ch.localAddress().asInstanceOf[InetSocketAddress],
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
  def apply(serverOptions: NettyFutureServerOptions = NettyFutureServerOptions.default)(implicit ec: ExecutionContext): NettyFutureServer =
    NettyFutureServer(Vector.empty, serverOptions)
}

case class NettyFutureServerBinding(localSocket: InetSocketAddress, stop: () => Future[Unit]) {
  def host: String = localSocket.getHostString
  def port: Int = localSocket.getPort
}
