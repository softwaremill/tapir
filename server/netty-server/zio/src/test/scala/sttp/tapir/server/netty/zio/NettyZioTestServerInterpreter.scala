package sttp.tapir.server.netty.zio

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import io.netty.channel.nio.NioEventLoopGroup
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.{NettyConfig, Route}
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.tests._
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

  override def server(
      routes: NonEmptyList[Task[Route[Task]]],
      gracefulShutdownTimeout: Option[FiniteDuration] = None
  ): Resource[IO, Port] = {
    val config = NettyConfig.default
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
      .make(bind)(server => IO.fromFuture[Unit](IO(Unsafe.unsafe(implicit u => runtime.unsafe.runToFuture(server.stop())))))
      .map(_.port)
  }
}
