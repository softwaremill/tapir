package sttp.tapir.server.netty

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import io.netty.channel.nio.NioEventLoopGroup
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.tests._

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration

class NettyFutureTestServerInterpreter(eventLoopGroup: NioEventLoopGroup)(implicit
    ec: ExecutionContext
) extends TestServerInterpreter[Future, Any, NettyFutureServerOptions, FutureRoute] {

  override def route(es: List[ServerEndpoint[Any, Future]], interceptors: Interceptors): FutureRoute = {
    val serverOptions = interceptors(NettyFutureServerOptions.customiseInterceptors).options
    NettyFutureServerInterpreter(serverOptions).toRoute(es)
  }

  override def serverWithStop(
      routes: NonEmptyList[FutureRoute],
      gracefulShutdownTimeout: Option[FiniteDuration] = None
  ): Resource[IO, (Port, KillSwitch)] = {
    val config =
      NettyConfig.default
        .eventLoopGroup(eventLoopGroup)
        .randomPort
        .withDontShutdownEventLoopGroupOnClose
        .noGracefulShutdown
    val customizedConfig = gracefulShutdownTimeout.map(config.withGracefulShutdownTimeout).getOrElse(config)
    val options = NettyFutureServerOptions.default
    val bind = IO.fromFuture(IO.delay(NettyFutureServer(options, customizedConfig).addRoutes(routes.toList).start()))

    Resource
      .make(bind.map(b => (b.port, IO.fromFuture(IO.delay(b.stop()))))) { case (_, release) => release }
  }
}
