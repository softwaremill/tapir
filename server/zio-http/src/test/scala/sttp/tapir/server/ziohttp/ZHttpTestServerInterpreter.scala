package sttp.tapir.server.ziohttp

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.Endpoint
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.decodefailure.DecodeFailureHandler
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.server.zhttp.{ZHttpInterpreter, ZHttpServerOptions}
import sttp.tapir.tests.Port
import zhttp.http._
import zhttp.service.Server
import zio.blocking.Blocking
import zio._

import scala.concurrent.Future
import scala.reflect.ClassTag

class ZHttpTestServerInterpreter[R <: Blocking]
    extends TestServerInterpreter[RIO[R, *], ZioStreams, Http[R, Throwable, Request, Response[R, Throwable]], ZioStreams] {

  override def route[I, E, O](
      e: ServerEndpoint[I, E, O, ZioStreams, RIO[R, *]],
      decodeFailureHandler: Option[DecodeFailureHandler] = None,
      metricsInterceptor: Option[MetricsRequestInterceptor[Future, ZioStreams]] = None
  ): Http[R, Throwable, Request, Response[R, Throwable]] = {
    val serverOptions: ZHttpServerOptions[R] = ZHttpServerOptions.default
    ZHttpInterpreter(serverOptions).toRoutes(e)
  }

  override def routeRecoverErrors[I, E <: Throwable, O](e: Endpoint[I, E, O, ZioStreams], fn: I => Future[O])(implicit
      eClassTag: ClassTag[E]
  ): Http[R, Throwable, Request, Response[R, Throwable]] = {
    ZHttpInterpreter().toRouteRecoverErrors(e)(fn)
  }

  override def server(routes: NonEmptyList[Http[R, Throwable, Request, Response[R, Throwable]]]): Resource[IO, Port] = {
    val app: HttpApp[Blocking, Throwable] = ZHttpInterpreter().toRoutes(routes)

    val zioHttpServerPort = 8090
    Server.start(zioHttpServerPort, app)
    Resource.pure[IO, Port](zioHttpServerPort)
  }
}
