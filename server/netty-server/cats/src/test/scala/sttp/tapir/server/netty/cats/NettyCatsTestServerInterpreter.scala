package sttp.tapir.server.netty.cats

import cats.data.NonEmptyList
import cats.effect.std.Dispatcher
import cats.effect.{IO, Resource}
import io.netty.channel.nio.NioEventLoopGroup
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.{NettyOptions, Route}
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.tests.Port

import java.net.InetSocketAddress

class NettyCatsTestServerInterpreter(eventLoopGroup: NioEventLoopGroup, dispatcher: Dispatcher[IO])
    extends TestServerInterpreter[IO, Any, NettyCatsServerOptions[IO, InetSocketAddress], Route[IO]] {
  override def route(es: List[ServerEndpoint[Any, IO]], interceptors: Interceptors): Route[IO] = {
    val serverOptions: NettyCatsServerOptions[IO, InetSocketAddress] = interceptors(
      NettyCatsServerOptions.customiseInterceptors[IO](dispatcher)
    ).options
    NettyCatsServerInterpreter(serverOptions).toRoute(es)
  }

  override def server(routes: NonEmptyList[Route[IO]]): Resource[IO, Port] = {
    val options =
      NettyCatsServerOptions
        .default[IO](dispatcher)
        .nettyOptions(NettyOptions.default.eventLoopGroup(eventLoopGroup).randomPort.noShutdownOnClose)
    val bind: IO[NettyCatsServerBinding[IO, InetSocketAddress]] = NettyCatsServer(options).addRoutes(routes.toList).start()

    Resource
      .make(bind)(_.stop())
      .map(_.port)
  }
}
