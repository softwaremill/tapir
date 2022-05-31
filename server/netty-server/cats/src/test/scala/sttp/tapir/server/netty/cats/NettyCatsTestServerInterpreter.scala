package sttp.tapir.server.netty.cats

import cats.data.NonEmptyList
import cats.effect.std.Dispatcher
import cats.effect.{IO, Resource}
import io.netty.channel.nio.NioEventLoopGroup
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.{NettyOptions, NettyOptionsBuilder, NettyServerType, Route}
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.tests.Port

class NettyCatsTestServerInterpreter(eventLoopGroup: NioEventLoopGroup, dispatcher: Dispatcher[IO])
    extends TestServerInterpreter[IO, Any, NettyCatsServerOptions[IO], Route[IO]] {
  private val definedOptions: NettyOptions =
    NettyOptionsBuilder.make().tcp().eventLoopGroup(eventLoopGroup).randomPort.noShutdownOnClose.build

  override def route(es: List[ServerEndpoint[Any, IO]], interceptors: Interceptors): Route[IO] = {
    val serverOptions: NettyCatsServerOptions[IO] = interceptors(
      NettyCatsServerOptions.customiseInterceptors[IO](dispatcher, definedOptions)
    ).options
    NettyCatsServerInterpreter(serverOptions).toRoute(es)
  }

  override def server(routes: NonEmptyList[Route[IO]]): Resource[IO, Port] = {
    val options =
      NettyCatsServerOptions
        .defaultTcp[IO](dispatcher)
        .nettyOptions(definedOptions)
    val bind: IO[NettyCatsServerBinding[IO, NettyServerType.TCP]] = NettyCatsServer(options).addRoutes(routes.toList).start()

    Resource
      .make(bind)(_.stop())
      .map(_.port)
  }
}
