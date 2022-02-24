package sttp.tapir.server.ziohttp

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureHandler, DefaultDecodeFailureHandler}
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.tests.Port
import zhttp.http._
import zhttp.service.{EventLoopGroup, Server, ServerChannelFactory}
import zio._
import zio.interop.catz._

class ZioHttpTestServerInterpreter(eventLoopGroup: EventLoopGroup, channelFactory: ServerChannelFactory)
    extends TestServerInterpreter[Task, ZioStreams, Http[Any, Throwable, Request, Response]] {

  override def route(
      e: ServerEndpoint[ZioStreams, Task],
      decodeFailureHandler: Option[DecodeFailureHandler],
      metricsInterceptor: Option[MetricsRequestInterceptor[Task]]
  ): Http[Any, Throwable, Request, Response] = {
    val serverOptions: ZioHttpServerOptions[Any] = ZioHttpServerOptions.customInterceptors
      .metricsInterceptor(metricsInterceptor)
      .decodeFailureHandler(decodeFailureHandler.getOrElse(DefaultDecodeFailureHandler.default))
      .options
    ZioHttpInterpreter(serverOptions).toHttp(e)
  }

  override def route(
      es: List[ServerEndpoint[ZioStreams, Task]]
  ): Http[Any, Throwable, Request, Response] =
    ZioHttpInterpreter().toHttp(es)

  override def server(routes: NonEmptyList[Http[Any, Throwable, Request, Response]]): Resource[IO, Port] = {
    implicit val r: Runtime[Any] = Runtime.default
    val env = ZEnvironment(eventLoopGroup).add(channelFactory)
    val server: Server[Any, Throwable] = Server.app(routes.toList.reduce(_ ++ _)) ++ Server.maxRequestSize(10000000)
    Server
      .make(server ++ Server.port(0))
      .provideEnvironment(env)
      .map(_.port)
      .toResource[IO]
  }
}
