package sttp.tapir.server.netty

import io.netty.channel._
import io.netty.channel.unix.DomainSocketAddress
import sttp.monad.{FutureMonad, MonadError}
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.internal.FutureUtil._
import sttp.tapir.server.netty.internal.{NettyBootstrap, NettyServerHandler}

import java.net.{InetSocketAddress, SocketAddress}
import java.nio.file.{Path, Paths}
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

case class NettyFutureServer(routes: Vector[FutureRoute], options: NettyFutureServerOptions, config: NettyConfig)(implicit
    ec: ExecutionContext
) {
  def addEndpoint(se: ServerEndpoint[Any, Future]): NettyFutureServer = addEndpoints(List(se))
  def addEndpoint(se: ServerEndpoint[Any, Future], overrideOptions: NettyFutureServerOptions): NettyFutureServer =
    addEndpoints(List(se), overrideOptions)
  def addEndpoints(ses: List[ServerEndpoint[Any, Future]]): NettyFutureServer = addRoute(
    NettyFutureServerInterpreter(options).toRoute(ses)
  )
  def addEndpoints(ses: List[ServerEndpoint[Any, Future]], overrideOptions: NettyFutureServerOptions): NettyFutureServer =
    addRoute(NettyFutureServerInterpreter(overrideOptions).toRoute(ses))

  def addRoute(r: FutureRoute): NettyFutureServer = copy(routes = routes :+ r)
  def addRoutes(r: Iterable[FutureRoute]): NettyFutureServer = copy(routes = routes ++ r)

  def options(o: NettyFutureServerOptions): NettyFutureServer = copy(options = o)

  def config(c: NettyConfig): NettyFutureServer = copy(config = c)
  def modifyConfig(f: NettyConfig => NettyConfig): NettyFutureServer = config(f(config))

  def host(h: String): NettyFutureServer = modifyConfig(_.host(h))

  def port(p: Int): NettyFutureServer = modifyConfig(_.port(p))

  def start(): Future[NettyFutureServerBinding] =
    startUsingSocketOverride[InetSocketAddress](None).map { case (socket, stop) =>
      NettyFutureServerBinding(socket, stop)
    }

  def startUsingDomainSocket(path: Option[Path] = None): Future[NettyFutureDomainSocketBinding] =
    startUsingDomainSocket(path.getOrElse(Paths.get(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString)))

  def startUsingDomainSocket(path: Path): Future[NettyFutureDomainSocketBinding] =
    startUsingSocketOverride(Some(new DomainSocketAddress(path.toFile))).map { case (socket, stop) =>
      NettyFutureDomainSocketBinding(socket, stop)
    }

  private def startUsingSocketOverride[SA <: SocketAddress](socketOverride: Option[SA]): Future[(SA, () => Future[Unit])] = {
    val eventLoopGroup = config.eventLoopConfig.initEventLoopGroup()
    implicit val monadError: MonadError[Future] = new FutureMonad()
    val route = Route.combine(routes)

    val channelFuture =
      NettyBootstrap(config, new NettyServerHandler(route, (f: () => Future[Unit]) => f()), eventLoopGroup, socketOverride)

    nettyChannelFutureToScala(channelFuture).map(ch => (ch.localAddress().asInstanceOf[SA], () => stop(ch, eventLoopGroup)))
  }

  private def stop(ch: Channel, eventLoopGroup: EventLoopGroup): Future[Unit] = {
    nettyFutureToScala(ch.close()).flatMap { _ =>
      if (config.shutdownEventLoopGroupOnClose) {
        nettyFutureToScala(eventLoopGroup.shutdownGracefully()).map(_ => ())
      } else Future.successful(())
    }
  }
}

object NettyFutureServer {
  def apply()(implicit ec: ExecutionContext): NettyFutureServer =
    NettyFutureServer(Vector.empty, NettyFutureServerOptions.default, NettyConfig.default)

  def apply(serverOptions: NettyFutureServerOptions)(implicit ec: ExecutionContext): NettyFutureServer =
    NettyFutureServer(Vector.empty, serverOptions, NettyConfig.default)

  def apply(config: NettyConfig)(implicit ec: ExecutionContext): NettyFutureServer =
    NettyFutureServer(Vector.empty, NettyFutureServerOptions.default, config)

  def apply(serverOptions: NettyFutureServerOptions, config: NettyConfig)(implicit ec: ExecutionContext): NettyFutureServer =
    NettyFutureServer(Vector.empty, serverOptions, config)
}

case class NettyFutureServerBinding(localSocket: InetSocketAddress, stop: () => Future[Unit]) {
  def hostName: String = localSocket.getHostName
  def port: Int = localSocket.getPort
}

case class NettyFutureDomainSocketBinding(localSocket: DomainSocketAddress, stop: () => Future[Unit]) {
  def path: String = localSocket.path()
}
