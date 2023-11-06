package sttp.tapir.server.netty

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import io.netty.channel.nio.NioEventLoopGroup
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.tests.Port

import scala.concurrent.{ExecutionContext, Future}

class NettyFutureTestServerInterpreter(eventLoopGroup: NioEventLoopGroup, customConfig: Option[NettyConfig => NettyConfig] = None)(implicit
    ec: ExecutionContext
) extends TestServerInterpreter[Future, Any, NettyFutureServerOptions, FutureRoute] {

  override def route(es: List[ServerEndpoint[Any, Future]], interceptors: Interceptors): FutureRoute = {
    val serverOptions = interceptors(NettyFutureServerOptions.customiseInterceptors).options
    NettyFutureServerInterpreter(serverOptions).toRoute(es)
  }

  override def serverWithStop(routes: NonEmptyList[FutureRoute]): Resource[IO, (Port, IO[Unit])] = {
    val config =
      NettyConfig.defaultNoStreaming
        .eventLoopGroup(eventLoopGroup)
        .randomPort
        .withDontShutdownEventLoopGroupOnClose
        .noGracefulShutdown
    val customizedConfig = customConfig.map(transformation => transformation(config)).getOrElse(config)
    val options = NettyFutureServerOptions.default
    val bind = IO.fromFuture(IO.delay(NettyFutureServer(options, customizedConfig).addRoutes(routes.toList).start()))

    Resource
      .eval(bind)
      .map(bind => (bind.port, IO.fromFuture(IO.delay(bind.stop()))))
  }
  
  override def server(routes: NonEmptyList[FutureRoute]): Resource[IO, Port] = {
    serverWithStop(routes).flatMap { case (port, stopServer) =>
      Resource.make(IO.pure(port))(_ => stopServer)
    }
  }
}
