package sttp.tapir.server.ziohttp

import cats.data.NonEmptyList
import cats.effect.{Async, IO, Resource}
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
import zio.interop.ZManagedSyntax
import zio.interop.catz.taskEffectInstance

import scala.reflect.ClassTag

class ZHttpTestServerInterpreter
  extends TestServerInterpreter[RIO[Blocking, *], ZioStreams, Http[Blocking, Throwable, Request, Response[Blocking, Throwable]], ZioStreams] {

  override def route[I, E, O](
      e: ServerEndpoint[I, E, O, ZioStreams, RIO[Blocking, *]],
      decodeFailureHandler: Option[DecodeFailureHandler],
      metricsInterceptor: Option[MetricsRequestInterceptor[RIO[Blocking, *], ZioStreams]]
  ): Http[Blocking, Throwable, Request, Response[Blocking, Throwable]] = {
    val serverOptions: ZHttpServerOptions[Blocking] = ZHttpServerOptions.default
    ZHttpInterpreter(serverOptions).toRoutes(e)
  }

  override def routeRecoverErrors[I, E <: Throwable, O](e: Endpoint[I, E, O, ZioStreams], fn: I => RIO[Blocking, O])(implicit
      eClassTag: ClassTag[E]
  ): Http[Blocking, Throwable, Request, Response[Blocking, Throwable]] = {
    ZHttpInterpreter().toRouteRecoverErrors(e)(fn)
  }

  override def server(routes: NonEmptyList[Http[Blocking, Throwable, Request, Response[Blocking, Throwable]]]): Resource[IO, Port] = {
    val as: Async[IO] = Async[IO]
    implicit val r: Runtime[Blocking] = Runtime.default
    val zioHttpServerPort = 8090
    val managed: ZManaged[Blocking, Throwable, Int] =
      Server.start(zioHttpServerPort, concatRoutes(routes)).toManaged(_ => URIO(zioHttpServerPort))

    new ZManagedSyntax(managed)
      .toResource(as, taskEffectInstance)
  }

  private def concatRoutes(
      routes: NonEmptyList[Http[Blocking, Throwable, Request, Response[Blocking, Throwable]]]
  ): Http[Blocking, Throwable, Request, Response[Blocking, Throwable]] = {
    routes.foldLeft[Http[Blocking, Throwable, Request, Response[Blocking, Throwable]]](Http.empty)((routes, route) => routes <> route)
  }
}
