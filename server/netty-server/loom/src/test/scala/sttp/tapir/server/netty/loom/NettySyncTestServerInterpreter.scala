package sttp.tapir.server.netty.loom

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import io.netty.channel.nio.NioEventLoopGroup
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.NettyConfig
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.tests.Port

import scala.concurrent.duration.FiniteDuration

class NettySyncTestServerInterpreter(eventLoopGroup: NioEventLoopGroup)
    extends TestServerInterpreter[Id, Any, NettySyncServerOptions, IdRoute] {
  override def route(es: List[ServerEndpoint[Any, Id]], interceptors: Interceptors): IdRoute = {
    val serverOptions: NettySyncServerOptions = interceptors(NettySyncServerOptions.customiseInterceptors).options
    NettySyncServerInterpreter(serverOptions).toRoute(es)
  }

  override def serverWithStop(
      routes: NonEmptyList[IdRoute],
      gracefulShutdownTimeout: Option[FiniteDuration] = None
  ): Resource[IO, (Port, IO[Unit])] = {
    val config =
      NettyConfig.default.eventLoopGroup(eventLoopGroup).randomPort.withDontShutdownEventLoopGroupOnClose.noGracefulShutdown
    val customizedConfig = gracefulShutdownTimeout.map(config.withGracefulShutdownTimeout).getOrElse(config)
    val options = NettySyncServerOptions.default
    val bind = IO.blocking(NettySyncServer(options, customizedConfig).addRoutes(routes.toList).start())

    Resource
      .make(bind.map(b => (b.port, IO.blocking(b.stop())))) { case (_, stop) => stop }
  }
}
