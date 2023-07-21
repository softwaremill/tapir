package sttp.tapir.server.netty

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import io.netty.channel.nio.NioEventLoopGroup
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.tests.Port

import scala.concurrent.{ExecutionContext, Future}

class NettyFutureTestServerInterpreter(eventLoopGroup: NioEventLoopGroup)(implicit ec: ExecutionContext)
    extends TestServerInterpreter[Future, Any, NettyFutureServerOptions, FutureRoute] {

  override def route(es: List[ServerEndpoint[Any, Future]], interceptors: Interceptors): FutureRoute = {
    val serverOptions = interceptors(NettyFutureServerOptions.customiseInterceptors).options
    NettyFutureServerInterpreter(serverOptions).toRoute(es)
  }

  override def server(routes: NonEmptyList[FutureRoute]): Resource[IO, Port] = {
    val config = NettyConfig.defaultNoStreaming.eventLoopGroup(eventLoopGroup).randomPort.withDontShutdownEventLoopGroupOnClose
    val options = NettyFutureServerOptions.default
    val bind = IO.fromFuture(IO.delay(NettyFutureServer(options, config).addRoutes(routes.toList).start()))

    Resource
      .make(bind)(binding => IO.fromFuture(IO.delay(binding.stop())))
      .map(b => b.port)
  }
}
