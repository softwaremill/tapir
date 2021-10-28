package sttp.tapir.server.netty

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import io.netty.channel.nio.NioEventLoopGroup
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureHandler, DefaultDecodeFailureHandler}
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.tests.Port

import scala.concurrent.{ExecutionContext, Future}

class NettyFutureTestServerInterpreter(eventLoopGroup: NioEventLoopGroup)(implicit ec: ExecutionContext)
    extends TestServerInterpreter[Future, Any, FutureRoute] {

  override def route[A, U, I, E, O](
      e: ServerEndpoint[A, U, I, E, O, Any, Future],
      decodeFailureHandler: Option[DecodeFailureHandler] = None,
      metricsInterceptor: Option[MetricsRequestInterceptor[Future]] = None
  ): FutureRoute = {
    val serverOptions: NettyFutureServerOptions = NettyFutureServerOptions.customInterceptors
      .metricsInterceptor(metricsInterceptor)
      .decodeFailureHandler(decodeFailureHandler.getOrElse(DefaultDecodeFailureHandler.handler))
      .options

    NettyFutureServerInterpreter(serverOptions).toRoute(List(e))
  }

  override def route[A, U, I, E, O](es: List[ServerEndpoint[A, U, I, E, O, Any, Future]]): FutureRoute = {
    NettyFutureServerInterpreter().toRoute(es)
  }

  override def server(routes: NonEmptyList[FutureRoute]): Resource[IO, Port] = {
    val options = NettyFutureServerOptions.default.nettyOptions(NettyOptions.default.eventLoopGroup(eventLoopGroup)).randomPort
    val bind = IO.fromFuture(IO.delay(NettyFutureServer(options).addRoutes(routes.toList).start()))

    Resource
      .make(bind)(binding => IO.fromFuture(IO.delay(binding.stop())))
      .map(_.port)
  }
}
