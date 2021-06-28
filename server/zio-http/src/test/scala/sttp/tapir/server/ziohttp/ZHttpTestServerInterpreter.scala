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
import zio._
import zio.blocking.Blocking

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

  override def routeRecoverErrors[I, E <: Throwable, O](e: Endpoint[I, E, O, ZioStreams], fn: I => RIO[R, O])(implicit
      eClassTag: ClassTag[E]
  ): Http[R, Throwable, Request, Response[R, Throwable]] = {
    ZHttpInterpreter().toRouteRecoverErrors(e)(fn)
  }

  override def server(routes: NonEmptyList[Http[R, Throwable, Request, Response[R, Throwable]]]): Resource[IO, Port] = {
    val zioHttpServerPort = 8090

    //    val bind = Server.start(zioHttpServerPort, concatRoutes(routes))
    //    ZManaged.fromEffect(bind)  // and convert Zmanaged to Cats.Resource https://github.com/zio/zio/issues/826

    Server.start(zioHttpServerPort, concatRoutes(routes))
    Resource.pure[IO, Port](zioHttpServerPort)
  }

  private def concatRoutes(routes: NonEmptyList[Http[R, Throwable, Request, Response[R, Throwable]]]): Http[R, Throwable, Request, Response[R, Throwable]] = {
    routes.foldLeft[Http[R, Throwable, Request, Response[R, Throwable]]](Http.empty)((routes, route) => routes <> route)
  }
}
