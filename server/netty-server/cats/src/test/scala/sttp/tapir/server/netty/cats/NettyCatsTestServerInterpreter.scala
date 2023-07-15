package sttp.tapir.server.netty.cats

import cats.data.NonEmptyList
import cats.effect.std.Dispatcher
import cats.effect.{IO, Resource}
import io.netty.channel.nio.NioEventLoopGroup
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.{NettyConfig, Route}
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.tests.Port
import sttp.capabilities.fs2.Fs2Streams

class NettyCatsTestServerInterpreter(eventLoopGroup: NioEventLoopGroup, dispatcher: Dispatcher[IO])
    extends TestServerInterpreter[IO, Fs2Streams[IO], NettyCatsServerOptions[IO], Route[IO]] {
  override def route(es: List[ServerEndpoint[Fs2Streams[IO], IO]], interceptors: Interceptors): Route[IO] = {
    val serverOptions: NettyCatsServerOptions[IO] = interceptors(
      NettyCatsServerOptions.customiseInterceptors[IO](dispatcher)
    ).options
    NettyCatsServerInterpreter(serverOptions).toRoute(es)
  }

  override def server(routes: NonEmptyList[Route[IO]]): Resource[IO, Port] = {
    val config = NettyConfig.defaultWithStreaming.eventLoopGroup(eventLoopGroup).randomPort.withDontShutdownEventLoopGroupOnClose
    val options = NettyCatsServerOptions.default[IO](dispatcher)
    val bind: IO[NettyCatsServerBinding[IO]] = NettyCatsServer(options, config).addRoutes(routes.toList).start()

    Resource
      .make(bind)(_.stop())
      .map(_.port)
  }
}
