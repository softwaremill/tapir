package sttp.tapir.server.ziohttp

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.Endpoint
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureHandler, DefaultDecodeFailureHandler}
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.tests.Port
import zhttp.http._
import zhttp.service.{EventLoopGroup, Server, ServerChannelFactory}
import zio._
import zio.interop.catz._

import java.util.concurrent.atomic.AtomicInteger
import scala.reflect.ClassTag

class ZioHttpTestServerInterpreter(nettyDeps: EventLoopGroup with ServerChannelFactory)
    extends TestServerInterpreter[Task, ZioStreams, Http[Any, Throwable, Request, Response[Any, Throwable]]] {

  override def route[I, E, O](
      e: ServerEndpoint[I, E, O, ZioStreams, Task],
      decodeFailureHandler: Option[DecodeFailureHandler],
      metricsInterceptor: Option[MetricsRequestInterceptor[Task]]
  ): Http[Any, Throwable, Request, Response[Any, Throwable]] = {
    val serverOptions: ZioHttpServerOptions[Any] = ZioHttpServerOptions.customInterceptors
      .metricsInterceptor(metricsInterceptor)
      .decodeFailureHandler(decodeFailureHandler.getOrElse(DefaultDecodeFailureHandler.handler))
      .options
    ZioHttpInterpreter(serverOptions).toHttp(e)
  }

  override def route[I, E, O](
      es: List[ServerEndpoint[I, E, O, ZioStreams, Task]]
  ): Http[Any, Throwable, Request, Response[Any, Throwable]] =
    ZioHttpInterpreter().toHttp(es)

  override def routeRecoverErrors[I, E <: Throwable, O](e: Endpoint[I, E, O, ZioStreams], fn: I => Task[O])(implicit
      eClassTag: ClassTag[E]
  ): Http[Any, Throwable, Request, Response[Any, Throwable]] =
    ZioHttpInterpreter().toHttpRecoverErrors(e)(fn)

  private val portCounter = new AtomicInteger(0) // no way to dynamically allocate ports

  override def server(routes: NonEmptyList[Http[Any, Throwable, Request, Response[Any, Throwable]]]): Resource[IO, Port] = {
    implicit val r: Runtime[Any] = Runtime.default
    val server: Server[Any, Throwable] = Server.app(routes.toList.reduce(_ <> _)) ++ Server.maxRequestSize(10000000)
    val port = ZManaged.fromEffect(UIO.effectTotal(8090 + portCounter.getAndIncrement()))
    port
      .flatMap(p =>
        Server
          .make(server ++ Server.port(p))
          .provide(nettyDeps)
          .map(_ => p)
      )
      .toResource[IO]
  }
}
