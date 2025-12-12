package sttp.tapir.server.netty.cats

import cats.effect.std.Dispatcher
import cats.effect.{IO, Resource}
import io.netty.channel.nio.NioEventLoopGroup
import sttp.capabilities.WebSockets
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.{NettyConfig, Route}
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.tests._
import sttp.capabilities.fs2.Fs2Streams
import scala.concurrent.duration.FiniteDuration

class NettyCatsTestServerInterpreter(eventLoopGroup: NioEventLoopGroup, dispatcher: Dispatcher[IO])
    extends TestServerInterpreter[IO, Fs2Streams[IO] with WebSockets, NettyCatsServerOptions[IO], Route[IO]] {
  override def route(es: List[ServerEndpoint[Fs2Streams[IO] with WebSockets, IO]], interceptors: Interceptors): Route[IO] = {
    val serverOptions: NettyCatsServerOptions[IO] = interceptors(
      NettyCatsServerOptions.customiseInterceptors[IO](dispatcher)
    ).options
    NettyCatsServerInterpreter(serverOptions).toRoute(es)
  }

  override def server(
      route: Route[IO],
      gracefulShutdownTimeout: Option[FiniteDuration] = None
  ): Resource[IO, Port] = {
    val config = NettyConfig.default
      .eventLoopGroup(eventLoopGroup)
      .randomPort
      .withDontShutdownEventLoopGroupOnClose
      .noGracefulShutdown

    val customizedConfig = gracefulShutdownTimeout.map(config.withGracefulShutdownTimeout).getOrElse(config)
    val options = NettyCatsServerOptions.default[IO](dispatcher)
    val bind: IO[NettyCatsServerBinding[IO]] = NettyCatsServer(options, customizedConfig).addRoute(route).start()

    Resource
      .make(bind)(_.stop())
      .map(_.port)
  }
}
