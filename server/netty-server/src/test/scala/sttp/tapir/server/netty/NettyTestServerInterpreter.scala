package sttp.tapir.server.netty

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import io.netty.channel.nio.NioEventLoopGroup
import sttp.tapir.Endpoint
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureHandler, DefaultDecodeFailureHandler}
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.tests.Port

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

class NettyTestServerInterpreter(eventLoopGroup: NioEventLoopGroup)(implicit ec: ExecutionContext)
    extends TestServerInterpreter[Future, Any, Route] {

  override def route[I, E, O](
      e: ServerEndpoint[I, E, O, Any, Future],
      decodeFailureHandler: Option[DecodeFailureHandler] = None,
      metricsInterceptor: Option[MetricsRequestInterceptor[Future]] = None
  ): Route = {
    val serverOptions: NettyServerOptions = NettyServerOptions.customInterceptors
      .metricsInterceptor(metricsInterceptor)
      .decodeFailureHandler(decodeFailureHandler.getOrElse(DefaultDecodeFailureHandler.handler))
      .options

    NettyServerInterpreter(serverOptions).toRoute(List(e))
  }

  override def route[I, E, O](es: List[ServerEndpoint[I, E, O, Any, Future]]): Route = {
    NettyServerInterpreter().toRoute(es)
  }

  override def routeRecoverErrors[I, E <: Throwable, O](e: Endpoint[I, E, O, Any], fn: I => Future[O])(implicit
      eClassTag: ClassTag[E]
  ): Route = {
    NettyServerInterpreter().toRoute(List(e.serverLogicRecoverErrors(fn)))
  }

  override def server(routes: NonEmptyList[Route]): Resource[IO, Port] = {
    val options = NettyServerOptions.default.nettyOptions(NettyOptions.default.eventLoopGroup(eventLoopGroup)).randomPort
    val bind = IO.fromFuture(IO.delay(NettyServer(options).addRoutes(routes.toList).start()))

    Resource
      .make(bind)(binding => IO.fromFuture(IO.delay(binding.stop())))
      .map(_.port)
  }
}
