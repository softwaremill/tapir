package sttp.tapir.server.netty

import cats.data.NonEmptyList
import cats.effect.std.Dispatcher
import cats.effect.{IO, Resource}
import io.netty.channel.nio.NioEventLoopGroup
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureHandler, DefaultDecodeFailureHandler}
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.tests.Port

class NettyCatsTestServerInterpreter(eventLoopGroup: NioEventLoopGroup, dispatcher: Dispatcher[IO])
    extends TestServerInterpreter[IO, Any, Route[IO]] {

  override def route(
      e: ServerEndpoint[Any, IO],
      decodeFailureHandler: Option[DecodeFailureHandler] = None,
      metricsInterceptor: Option[MetricsRequestInterceptor[IO]] = None
  ): Route[IO] = {
    val serverOptions: NettyCatsServerOptions[IO] = NettyCatsServerOptions
      .customInterceptors[IO](dispatcher)
      .metricsInterceptor(metricsInterceptor)
      .decodeFailureHandler(decodeFailureHandler.getOrElse(DefaultDecodeFailureHandler.handler))
      .options

    NettyCatsServerInterpreter(serverOptions).toRoute(List(e))
  }

  override def route(es: List[ServerEndpoint[Any, IO]]): Route[IO] =
    NettyCatsServerInterpreter[IO](dispatcher).toRoute(es)

  override def server(routes: NonEmptyList[Route[IO]]): Resource[IO, Port] = {
    val options =
      NettyCatsServerOptions.default[IO](dispatcher).nettyOptions(NettyOptions.default.eventLoopGroup(eventLoopGroup)).randomPort
    val bind = NettyCatsServer(options).addRoutes(routes.toList).start()

    Resource
      .make(bind)(_.stop())
      .map(_.port)
  }
}
