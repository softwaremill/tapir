package sttp.tapir.server.netty

import cats.effect.std.Dispatcher
import cats.effect.{Async, IO, Resource}
import cats.syntax.all._
import io.netty.channel._
import io.netty.channel.unix.DomainSocketAddress
import sttp.monad.MonadError
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.internal.CatsUtil._
import sttp.tapir.server.netty.internal.{NettyBootstrap, NettyServerHandler}

import java.net.{InetSocketAddress, SocketAddress}
import java.nio.file.Path

case class NettyCatsServer[F[_]: Async, S <: NettyServerType](routes: Vector[Route[F]], options: NettyCatsServerOptions[F]) {
  def addEndpoint(se: ServerEndpoint[Any, F]): NettyCatsServer[F, S] = addEndpoints(List(se))
  def addEndpoint(se: ServerEndpoint[Any, F], overrideOptions: NettyCatsServerOptions[F]): NettyCatsServer[F, S] =
    addEndpoints(List(se), overrideOptions)
  def addEndpoints(ses: List[ServerEndpoint[Any, F]]): NettyCatsServer[F, S] = addRoute(
    NettyCatsServerInterpreter(options).toRoute(ses)
  )
  def addEndpoints(ses: List[ServerEndpoint[Any, F]], overrideOptions: NettyCatsServerOptions[F]): NettyCatsServer[F, S] = addRoute(
    NettyCatsServerInterpreter(overrideOptions).toRoute(ses)
  )

  def addRoute(r: Route[F]): NettyCatsServer[F, S] = copy(routes = routes :+ r)
  def addRoutes(r: Iterable[Route[F]]): NettyCatsServer[F, S] = copy(routes = routes ++ r)

  def options(o: NettyCatsServerOptions[F]): NettyCatsServer[F, S] = copy(options = o)

  def host(hostname: String)(implicit isTCP: S =:= NettyServerType.TCP): NettyCatsServer[F, S] = {
    val nettyOptions = options.nettyOptions.host(hostname)

    options(options.nettyOptions(nettyOptions))
  }

  def port(p: Int)(implicit isTCP: S =:= NettyServerType.TCP): NettyCatsServer[F, S] = {
    val nettyOptions = options.nettyOptions.port(p)

    options(options.nettyOptions(nettyOptions))
  }

  def path(path: Path)(implicit isDomainSocket: S =:= NettyServerType.DomainSocket): NettyCatsServer[F, S] = {
    val nettyOptions = options.nettyOptions.path(path)

    options(options.nettyOptions(nettyOptions))
  }

  def start(): F[NettyCatsServerBinding[F, S]] = Async[F].defer {
    val eventLoopGroup = options.nettyOptions.eventLoopConfig.initEventLoopGroup()
    implicit val monadError: MonadError[F] = new CatsMonadError[F]()
    val route: Route[F] = Route.combine(routes)

    val channelFuture = NettyBootstrap(
      options.nettyOptions,
      new NettyServerHandler(route, (f: F[Unit]) => options.dispatcher.unsafeToFuture(f)),
      eventLoopGroup
    )

    nettyChannelFutureToScala(channelFuture).map(ch =>
      NettyCatsServerBinding(
        ch.localAddress(),
        () => stop(ch, eventLoopGroup)
      )
    )
  }

  private def stop(ch: Channel, eventLoopGroup: EventLoopGroup): F[Unit] = {
    Async[F].defer {
      nettyFutureToScala(ch.close()).flatMap { _ =>
        if (options.nettyOptions.shutdownEventLoopGroupOnClose) {
          nettyFutureToScala(eventLoopGroup.shutdownGracefully()).map(_ => ())
        } else Async[F].unit
      }
    }
  }
}

object NettyCatsServer {
  def apply[F[_]: Async](dispatcher: Dispatcher[F]): NettyCatsServer[F, NettyServerType.TCP] =
    tcp(dispatcher)

  def apply[F[_]: Async, S <: NettyServerType](options: NettyCatsServerOptions[F]): NettyCatsServer[F, S] =
    NettyCatsServer(Vector.empty, options)

  def io(): Resource[IO, NettyCatsServer[IO, NettyServerType.TCP]] = Dispatcher[IO].map(apply[IO](_))

  def tcp[F[_]: Async](dispatcher: Dispatcher[F]): NettyCatsServer[F, NettyServerType.TCP] = {
    NettyCatsServer(Vector.empty, NettyCatsServerOptions.defaultTcp[F](dispatcher))
  }

  def domainSocket[F[_]: Async](dispatcher: Dispatcher[F]): NettyCatsServer[F, NettyServerType.DomainSocket] = {
    NettyCatsServer[F, NettyServerType.DomainSocket](Vector.empty, NettyCatsServerOptions.defaultDomainSocket[F](dispatcher))
  }

}

case class NettyCatsServerBinding[F[_], S <: NettyServerType](localSocket: SocketAddress, stop: () => F[Unit]) {
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
