package sttp.tapir.server.netty

import cats.effect.{Async, IO, Resource}
import cats.effect.std.Dispatcher
import cats.syntax.all._
import io.netty.channel._
import sttp.monad.MonadError
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.internal.CatsUtil._
import sttp.tapir.server.netty.internal.{NettyBootstrap, NettyServerHandler}

import java.net.{InetSocketAddress, SocketAddress}

case class NettyCatsServer[F[_]: Async, S <: SocketAddress](routes: Vector[Route[F]], options: NettyCatsServerOptions[F]) {
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
  def start(): F[NettyCatsServerBinding[F]] = Async[F].defer {
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
  def apply[F[_]: Async](dispatcher: Dispatcher[F]): NettyCatsServer[F, InetSocketAddress] =
    NettyCatsServer(Vector.empty, NettyCatsServerOptions.default[F](dispatcher))

  def apply[F[_]: Async, S <: SocketAddress](options: NettyCatsServerOptions[F]): NettyCatsServer[F, S] =
    NettyCatsServer(Vector.empty, options)

  def io(): Resource[IO, NettyCatsServer[IO, InetSocketAddress]] = Dispatcher[IO].map(apply[IO](_))
}

case class NettyCatsServerBinding[F[_]](localSocket: InetSocketAddress, stop: () => F[Unit]) {
  def host: String = localSocket.getHostString
  def port: Int = localSocket.getPort
}
