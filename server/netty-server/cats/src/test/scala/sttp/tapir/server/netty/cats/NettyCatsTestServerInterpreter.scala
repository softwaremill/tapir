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
import scala.concurrent.duration.FiniteDuration

class NettyCatsTestServerInterpreter(eventLoopGroup: NioEventLoopGroup, dispatcher: Dispatcher[IO])
    extends TestServerInterpreter[IO, Fs2Streams[IO], NettyCatsServerOptions[IO], Route[IO]] {
  override def route(es: List[ServerEndpoint[Fs2Streams[IO], IO]], interceptors: Interceptors): Route[IO] = {
    val serverOptions: NettyCatsServerOptions[IO] = interceptors(
      NettyCatsServerOptions.customiseInterceptors[IO](dispatcher)
    ).options
    NettyCatsServerInterpreter(serverOptions).toRoute(es)
  }

  override def serverWithStop(
      routes: NonEmptyList[Route[IO]],
      gracefulShutdownTimeout: Option[FiniteDuration] = None
  ): Resource[IO, (Port, IO[Unit])] = {
    val config = NettyConfig.defaultWithStreaming
      .eventLoopGroup(eventLoopGroup)
      .randomPort
      .withDontShutdownEventLoopGroupOnClose
      .maxContentLength(NettyCatsTestServerInterpreter.maxContentLength)
      .noGracefulShutdown

    val customizedConfig = gracefulShutdownTimeout.map(config.withGracefulShutdownTimeout).getOrElse(config)
    val options = NettyCatsServerOptions.default[IO](dispatcher)
    val bind: IO[NettyCatsServerBinding[IO]] = NettyCatsServer(options, customizedConfig).addRoutes(routes.toList).start()

    Resource
      .make(bind.map(b => (b, b.stop()))) { case (_, stop) => stop }
      .map { case (b, stop) => (b.port, stop) }
  }

  override def server(routes: NonEmptyList[Route[IO]]): Resource[IO, Port] = {
    serverWithStop(routes).map(_._1)
  }
}

object NettyCatsTestServerInterpreter {
  val maxContentLength = 10000
}
