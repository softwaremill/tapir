package sttp.tapir.server.netty.loom

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import io.netty.channel.nio.NioEventLoopGroup
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.NettyConfig
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.tests.Port

import scala.concurrent.duration.FiniteDuration
import ox.*
import sttp.capabilities.WebSockets

class NettyIdTestServerInterpreter(eventLoopGroup: NioEventLoopGroup)
    extends TestServerInterpreter[Id, OxStreams with WebSockets, NettyIdServerOptions, IdRoute] {
  override def route(es: List[ServerEndpoint[OxStreams with WebSockets, Id]], interceptors: Interceptors): IdRoute = {
    val serverOptions: NettyIdServerOptions = interceptors(NettyIdServerOptions.customiseInterceptors).options
    scoped {
      NettyIdServerInterpreter(serverOptions).toRoute(es)
    }
  }

  def route(es: List[ServerEndpoint[OxStreams with WebSockets, Id]], interceptors: Interceptors)(using Ox): IdRoute = {
    val serverOptions: NettyIdServerOptions = interceptors(NettyIdServerOptions.customiseInterceptors).options
    NettyIdServerInterpreter(serverOptions).toRoute(es)
  }

  override def serverWithStop(
      routes: NonEmptyList[IdRoute],
      gracefulShutdownTimeout: Option[FiniteDuration] = None
  ): Resource[IO, (Port, IO[Unit])] = {
    val config =
      NettyConfig.default.eventLoopGroup(eventLoopGroup).randomPort.withDontShutdownEventLoopGroupOnClose.noGracefulShutdown
    val customizedConfig = gracefulShutdownTimeout.map(config.withGracefulShutdownTimeout).getOrElse(config)
    val options = NettyIdServerOptions.default
    val bind = IO.blocking(NettyIdServer(options, customizedConfig).start(routes.toList))

    Resource
      .make(bind.map(b => (b.port, IO.blocking(b.stop())))) { case (_, stop) => stop }
  }

  def scopedServerWithRoutesStop(
      routes: NonEmptyList[IdRoute],
      gracefulShutdownTimeout: Option[FiniteDuration] = None
    )(using Ox): NettyIdServerBinding = 
    val config =
      NettyConfig.default.eventLoopGroup(eventLoopGroup).randomPort.withDontShutdownEventLoopGroupOnClose.noGracefulShutdown
    val customizedConfig = gracefulShutdownTimeout.map(config.withGracefulShutdownTimeout).getOrElse(config)
    val options = NettyIdServerOptions.default
    val interpreter = NettyIdServerInterpreter(options)
    useInScope(NettyIdServer(options, customizedConfig).start(routes.toList))(_.stop())

  def scopedServerWithInterceptorsStop(
    endpoint: ServerEndpoint[OxStreams with WebSockets, Id],
    interceptors: Interceptors = identity,
    gracefulShutdownTimeout: Option[FiniteDuration] = None
  )(using Ox): NettyIdServerBinding =
    val config =
      NettyConfig.default.eventLoopGroup(eventLoopGroup).randomPort.withDontShutdownEventLoopGroupOnClose.noGracefulShutdown
    val customizedConfig = gracefulShutdownTimeout.map(config.withGracefulShutdownTimeout).getOrElse(config)
    val options = interceptors(NettyIdServerOptions.customiseInterceptors).options
    val interpreter = NettyIdServerInterpreter(options)
    useInScope(NettyIdServer(customizedConfig).addEndpoint(endpoint, options).start())(_.stop())

  def scopedServerWithStop(
    endpoints: NonEmptyList[ServerEndpoint[OxStreams with WebSockets, Id]],
    gracefulShutdownTimeout: Option[FiniteDuration] = None
  )(using Ox): NettyIdServerBinding =
    val config =
      NettyConfig.default.eventLoopGroup(eventLoopGroup).randomPort.withDontShutdownEventLoopGroupOnClose.noGracefulShutdown
    val customizedConfig = gracefulShutdownTimeout.map(config.withGracefulShutdownTimeout).getOrElse(config)
    val options = NettyIdServerOptions.default
    val interpreter = NettyIdServerInterpreter(options)
    useInScope(NettyIdServer(options, customizedConfig).start(List(interpreter.toRoute(endpoints.toList))))(_.stop())
}
