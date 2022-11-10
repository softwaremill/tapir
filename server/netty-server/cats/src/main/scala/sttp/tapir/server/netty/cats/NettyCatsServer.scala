package sttp.tapir.server.netty.cats

import cats.effect.std.Dispatcher
import cats.effect.{Async, IO, Resource}
import cats.syntax.all._
import io.netty.channel._
import io.netty.channel.unix.DomainSocketAddress
import sttp.monad.MonadError
import sttp.tapir.integ.cats.CatsMonadError
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.Route
import sttp.tapir.server.netty.cats.internal.CatsUtil.{nettyChannelFutureToScala, nettyFutureToScala}
import sttp.tapir.server.netty.internal.{NettyBootstrap, NettyServerHandler}

import java.net.{InetSocketAddress, SocketAddress}
import java.nio.file.Path

case class NettyCatsServer[F[_]: Async, SA <: SocketAddress](routes: Vector[Route[F]], options: NettyCatsServerOptions[F, SA]) {
  def addEndpoint(se: ServerEndpoint[Any, F]): NettyCatsServer[F, SA] = addEndpoints(List(se))
  def addEndpoint(se: ServerEndpoint[Any, F], overrideOptions: NettyCatsServerOptions[F, SA]): NettyCatsServer[F, SA] =
    addEndpoints(List(se), overrideOptions)
  def addEndpoints(ses: List[ServerEndpoint[Any, F]]): NettyCatsServer[F, SA] = addRoute(
    NettyCatsServerInterpreter(options).toRoute(ses)
  )
  def addEndpoints(ses: List[ServerEndpoint[Any, F]], overrideOptions: NettyCatsServerOptions[F, SA]): NettyCatsServer[F, SA] = addRoute(
    NettyCatsServerInterpreter(overrideOptions).toRoute(ses)
  )

  def addRoute(r: Route[F]): NettyCatsServer[F, SA] = copy(routes = routes :+ r)
  def addRoutes(r: Iterable[Route[F]]): NettyCatsServer[F, SA] = copy(routes = routes ++ r)

  def options[SA2 <: SocketAddress](o: NettyCatsServerOptions[F, SA2]): NettyCatsServer[F, SA2] = copy(options = o)

  def host(hostname: String)(implicit isTCP: SA =:= InetSocketAddress): NettyCatsServer[F, InetSocketAddress] = {
    val nettyOptions = options.nettyOptions.host(hostname)
    options(options.nettyOptions(nettyOptions))
  }

  def port(p: Int)(implicit isTCP: SA =:= InetSocketAddress): NettyCatsServer[F, InetSocketAddress] = {
    val nettyOptions = options.nettyOptions.port(p)
    options(options.nettyOptions(nettyOptions))
  }

  def domainSocketPath(path: Path)(implicit isDomainSocket: SA =:= DomainSocketAddress): NettyCatsServer[F, DomainSocketAddress] = {
    val nettyOptions = options.nettyOptions.domainSocketPath(path)
    options(options.nettyOptions(nettyOptions))
  }

  def start(): F[NettyCatsServerBinding[F, SA]] = Async[F].defer {
    val eventLoopGroup = options.nettyOptions.eventLoopConfig.initEventLoopGroup()
    implicit val monadError: MonadError[F] = new CatsMonadError[F]()
    val route: Route[F] = Route.combine(routes)

    val channelFuture = NettyBootstrap(
      options.nettyOptions,
      new NettyServerHandler(route, (f: () => F[Unit]) => options.dispatcher.unsafeToFuture(f())),
      eventLoopGroup
    )

    nettyChannelFutureToScala(channelFuture).map(ch =>
      NettyCatsServerBinding(
        ch.localAddress().asInstanceOf[SA],
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
  def apply[F[_]: Async](dispatcher: Dispatcher[F]): NettyCatsServer[F, InetSocketAddress] =
    apply(NettyCatsServerOptions.default(dispatcher))

  def apply[F[_]: Async, SA <: SocketAddress](options: NettyCatsServerOptions[F, SA]): NettyCatsServer[F, SA] =
    NettyCatsServer(Vector.empty, options)

  def io(): Resource[IO, NettyCatsServer[IO, InetSocketAddress]] = Dispatcher[IO].map(apply[IO](_))
}

case class NettyCatsServerBinding[F[_], SA <: SocketAddress](localSocket: SA, stop: () => F[Unit]) {
  def hostName(implicit isTCP: SA =:= InetSocketAddress): String = isTCP(localSocket).getHostName
  def port(implicit isTCP: SA =:= InetSocketAddress): Int = isTCP(localSocket).getPort
  def path(implicit isDomainSocket: SA =:= DomainSocketAddress): String = isDomainSocket(localSocket).path()
}
