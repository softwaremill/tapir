package sttp.tapir.server.netty

import cats.effect.{Async, IO, Resource}
import cats.effect.std.Dispatcher
import cats.syntax.all._
import io.netty.channel._
import sttp.monad.MonadError
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.internal.CatsUtil._
import sttp.tapir.server.netty.internal.{NettyBootstrap, NettyServerHandler}

import java.net.InetSocketAddress

case class NettyCatsServer[F[_]: Async](routes: Vector[Route[F]], options: NettyCatsServerOptions[F]) {
  def addEndpoint(se: ServerEndpoint[Any, F]): NettyCatsServer[F] = addEndpoints(List(se))
  def addEndpoint(se: ServerEndpoint[Any, F], overrideOptions: NettyCatsServerOptions[F]): NettyCatsServer[F] =
    addEndpoints(List(se), overrideOptions)
  def addEndpoints(ses: List[ServerEndpoint[Any, F]]): NettyCatsServer[F] = addRoute(
    NettyCatsServerInterpreter(options).toRoute(ses)
  )
  def addEndpoints(ses: List[ServerEndpoint[Any, F]], overrideOptions: NettyCatsServerOptions[F]): NettyCatsServer[F] =
    addRoute(
      NettyCatsServerInterpreter(overrideOptions).toRoute(ses)
    )

  def addRoute(r: Route[F]): NettyCatsServer[F] = copy(routes = routes :+ r)
  def addRoutes(r: Iterable[Route[F]]): NettyCatsServer[F] = copy(routes = routes ++ r)

  def options(o: NettyCatsServerOptions[F]): NettyCatsServer[F] = copy(options = o)
  def host(s: String): NettyCatsServer[F] = copy(options = options.host(s))
  def port(p: Int): NettyCatsServer[F] = copy(options = options.port(p))

  def start(): F[NettyCatsServerBinding[F]] = Async[F].defer {
    val eventLoopGroup = options.nettyOptions.eventLoopGroup()
    implicit val monadError: MonadError[F] = new CatsMonadError[F]()
    val route: Route[F] = Route.combine(routes)

    val channelFuture = NettyBootstrap(
      options.nettyOptions,
      new NettyServerHandler(route, (f: F[Unit]) => options.dispatcher.unsafeToFuture(f)),
      eventLoopGroup,
      options.host,
      options.port
    )

    nettyChannelFutureToScala(channelFuture).map(ch =>
      NettyCatsServerBinding(
        ch.localAddress().asInstanceOf[InetSocketAddress],
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
  def apply[F[_]: Async](dispatcher: Dispatcher[F]): NettyCatsServer[F] =
    NettyCatsServer(Vector.empty, NettyCatsServerOptions.default[F](dispatcher))

  def apply[F[_]: Async](options: NettyCatsServerOptions[F]): NettyCatsServer[F] =
    NettyCatsServer(Vector.empty, options)

  def io(): Resource[IO, NettyCatsServer[IO]] = Dispatcher[IO].map(apply[IO](_))
}

case class NettyCatsServerBinding[F[_]](localSocket: InetSocketAddress, stop: () => F[Unit]) {
  def host: String = localSocket.getHostString
  def port: Int = localSocket.getPort
}
