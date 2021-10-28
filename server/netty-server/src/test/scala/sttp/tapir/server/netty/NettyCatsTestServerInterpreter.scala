package sttp.tapir.server.netty

import cats.data.NonEmptyList
import cats.effect.std.Dispatcher
import cats.effect.{IO, Resource}
import io.netty.channel.nio.NioEventLoopGroup
import sttp.tapir.Endpoint
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureHandler, DefaultDecodeFailureHandler}
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.server.netty.NettyOptions.EventLoopConfig
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.tests.Port

import scala.reflect.ClassTag

class NettyCatsTestServerInterpreter(eventLoopGroup: NioEventLoopGroup, dispatcher: Dispatcher[IO])
    extends TestServerInterpreter[IO, Any, Route[IO]] {

  override def route[I, E, O](
      e: ServerEndpoint[I, E, O, Any, IO],
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

  override def route[I, E, O](es: List[ServerEndpoint[I, E, O, Any, IO]]): Route[IO] =
    NettyCatsServerInterpreter[IO](dispatcher).toRoute(es)

  override def routeRecoverErrors[I, E <: Throwable, O](e: Endpoint[I, E, O, Any], fn: I => IO[O])(implicit
      eClassTag: ClassTag[E]
  ): Route[IO] =
    NettyCatsServerInterpreter[IO](dispatcher).toRoute(List(e.serverLogicRecoverErrors(fn)))

  override def server(routes: NonEmptyList[Route[IO]]): Resource[IO, Port] = {
    val options =
      NettyCatsServerOptions
        .default[IO](dispatcher)
        .nettyOptions(NettyOptionsBuilder.make().tcp().eventLoopGroup(eventLoopGroup).randomPort.noShutdownOnClose.build)
    val bind = NettyCatsServer(options).addRoutes(routes.toList).start()

    Resource
      .make(bind)(_.stop())
      .map(_.port)
  }
}
