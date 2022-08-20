package sttp.tapir.server.netty

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import io.netty.channel.nio.NioEventLoopGroup
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.tests.Port

import java.net.InetSocketAddress
import scala.concurrent.{ExecutionContext, Future}

class NettyFutureTestServerInterpreter(eventLoopGroup: NioEventLoopGroup)(implicit ec: ExecutionContext)
    extends TestServerInterpreter[Future, Any, NettyFutureServerOptions[InetSocketAddress], FutureRoute] {

  override def route(es: List[ServerEndpoint[Any, Future]], interceptors: Interceptors): FutureRoute = {
    val serverOptions = interceptors(NettyFutureServerOptions.customiseInterceptors).options
    NettyFutureServerInterpreter(serverOptions).toRoute(es)
  }

  override def server(routes: NonEmptyList[FutureRoute]): Resource[IO, Port] = {
    val options = NettyFutureServerOptions.default.nettyOptions(
      NettyOptions.default.eventLoopGroup(eventLoopGroup).randomPort.noShutdownOnClose
    )
    val bind = IO.fromFuture(IO.delay(NettyFutureServer(options).addRoutes(routes.toList).start()))

    Resource
      .make(bind)(binding => IO.fromFuture(IO.delay(binding.stop())))
      .map(b => b.port)
  }
}
