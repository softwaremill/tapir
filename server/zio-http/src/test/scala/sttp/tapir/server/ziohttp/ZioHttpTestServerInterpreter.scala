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
import zhttp.service.server.ServerChannelFactory
import zhttp.service.{EventLoopGroup, Server}
import zio._
import zio.interop.catz._
import zio.stream.Stream

import java.util.concurrent.atomic.AtomicInteger
import scala.reflect.ClassTag

class ZioHttpTestServerInterpreter
    extends TestServerInterpreter[Task, ZioStreams, Http[Any, Throwable, Request, Response[Any, Throwable]], Stream[Throwable, Byte]] {

  override def route[I, E, O](
      e: ServerEndpoint[I, E, O, ZioStreams, Task],
      decodeFailureHandler: Option[DecodeFailureHandler],
      metricsInterceptor: Option[MetricsRequestInterceptor[Task, Stream[Throwable, Byte]]]
  ): Http[Any, Throwable, Request, Response[Any, Throwable]] = {
    val serverOptions: ZioHttpServerOptions[Any] = ZioHttpServerOptions.customInterceptors(
      metricsInterceptor = metricsInterceptor,
      decodeFailureHandler = decodeFailureHandler.getOrElse(DefaultDecodeFailureHandler.handler)
    )
    ZioHttpInterpreter(serverOptions).toHttp(e)
  }

  override def routeRecoverErrors[I, E <: Throwable, O](e: Endpoint[I, E, O, ZioStreams], fn: I => Task[O])(implicit
      eClassTag: ClassTag[E]
  ): Http[Any, Throwable, Request, Response[Any, Throwable]] =
    ZioHttpInterpreter().toHttpRecoverErrors(e)(fn)

  private val portCounter = new AtomicInteger(0) // no way to dynamically allocate ports

  override def server(routes: NonEmptyList[Http[Any, Throwable, Request, Response[Any, Throwable]]]): Resource[IO, Port] = {
    implicit val r: Runtime[Any] = Runtime.default
    val server: Server[Any, Throwable] = Server.app(routes.toList.reduce(_ <> _))
    val port = ZManaged.fromEffect(UIO.effectTotal(8090 + portCounter.getAndIncrement()))
    port
      .flatMap(p =>
        Server
          .make(server ++ Server.port(p))
          .provideLayer(EventLoopGroup.auto(0) ++ ServerChannelFactory.auto)
          .map(_ => p)
      )
      .toResource[IO]
  }
}
