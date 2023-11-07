package sttp.tapir.server.netty.zio

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import io.netty.channel.nio.NioEventLoopGroup
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.{NettyConfig, Route}
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.tests.Port
import zio.{Runtime, Task, Unsafe}

import scala.concurrent.duration.FiniteDuration

class NettyZioTestServerInterpreter[R](eventLoopGroup: NioEventLoopGroup)
    extends TestServerInterpreter[Task, ZioStreams, NettyZioServerOptions[Any], Task[Route[Task]]] {
  override def route(es: List[ServerEndpoint[ZioStreams, Task]], interceptors: Interceptors): Task[Route[Task]] = {
    val serverOptions = interceptors(
      NettyZioServerOptions.customiseInterceptors
    ).options
    NettyZioServerInterpreter(serverOptions).toRoute(es)
  }

  override def serverWithStop(
      routes: NonEmptyList[Task[Route[Task]]],
      gracefulShutdownTimeout: Option[FiniteDuration] = None
  ): Resource[IO, (Port, IO[Unit])] = {
    val config = NettyConfig.defaultWithStreaming
      .eventLoopGroup(eventLoopGroup)
      .randomPort
      .withDontShutdownEventLoopGroupOnClose
      .noGracefulShutdown

    val customizedConfig = gracefulShutdownTimeout.map(config.withGracefulShutdownTimeout).getOrElse(config)
    val options = NettyZioServerOptions.default[R]

    val runtime: Runtime[R] = Runtime.default.asInstanceOf[Runtime[R]]

    val bind: IO[NettyZioServerBinding[R]] =
      IO.fromFuture(
        IO.delay(
          Unsafe.unsafe(implicit u =>
            runtime.unsafe.runToFuture(NettyZioServer(options, customizedConfig).addRoutes(routes.toList).start())
          )
        )
      )

    Resource
      .make(bind.map(b => (b, IO.fromFuture[Unit](IO(Unsafe.unsafe(implicit u => runtime.unsafe.runToFuture(b.stop()))))))) {
        case (_, stop) => stop
      }
      .map { case (b, stop) => (b.port, stop) }
  }

  override def server(routes: NonEmptyList[Task[Route[Task]]]): Resource[IO, Port] = {
    serverWithStop(routes).map(_._1)
  }
}
