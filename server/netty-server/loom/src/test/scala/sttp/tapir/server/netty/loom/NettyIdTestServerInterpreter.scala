package sttp.tapir.server.netty.loom

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import io.netty.channel.nio.NioEventLoopGroup
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.NettyConfig
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.tests.Port

class NettyIdTestServerInterpreter(eventLoopGroup: NioEventLoopGroup)
    extends TestServerInterpreter[Id, Any, NettyIdServerOptions, IdRoute] {
  override def route(es: List[ServerEndpoint[Any, Id]], interceptors: Interceptors): IdRoute = {
    val serverOptions: NettyIdServerOptions = interceptors(NettyIdServerOptions.customiseInterceptors).options
    NettyIdServerInterpreter(serverOptions).toRoute(es)
  }

  override def server(routes: NonEmptyList[IdRoute]): Resource[IO, Port] = {
    val config =
      NettyConfig.defaultNoStreaming.eventLoopGroup(eventLoopGroup).randomPort.withDontShutdownEventLoopGroupOnClose.noGracefulShutdown
    val options = NettyIdServerOptions.default
    val bind = IO.blocking(NettyIdServer(options, config).addRoutes(routes.toList).start())

    Resource
      .make(bind)(binding => IO.blocking(binding.stop()))
      .map(b => b.port)
  }
}
