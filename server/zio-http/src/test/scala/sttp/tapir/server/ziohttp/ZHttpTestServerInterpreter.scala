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
import zhttp.http.{Http, HttpApp, Request, Response}
import zhttp.service.Server
import zio.blocking.Blocking
import zio.{RIO, ZIO}

import scala.concurrent.Future
import scala.reflect.ClassTag

class ZHttpTestServerInterpreter[R] extends TestServerInterpreter[RIO[R, *], ZioStreams, Http[R, Throwable, Request, Response[R, Throwable]], ZioStreams] {

  override def route[I, E, O](
                               e: ServerEndpoint[I, E, O, ZioStreams, RIO[R, *]],
                               decodeFailureHandler: Option[DecodeFailureHandler] = None,
                               metricsInterceptor: Option[MetricsRequestInterceptor[Future, ZioStreams]] = None
                             ): Http[R, Throwable, Request, Response[R, Throwable]] = {
    val serverOptions: ZHttpServerOptions[R] = ZHttpServerOptions.default
    ZHttpInterpreter(serverOptions).toHttp(e.endpoint)()
  }

  override def routeRecoverErrors[I, E <: Throwable, O](e: Endpoint[I, E, O, ZioStreams], fn: I => Future[O])(implicit
                                                                                                              eClassTag: ClassTag[E]
  ): Http[R, Throwable, Request, Response[R, Throwable]] = {
    ZHttpInterpreter().toRouteRecoverErrors(e)(fn)
  }

  override def server(routes: NonEmptyList[Http[R, Throwable, Request, Response[R, Throwable]]]): Resource[IO, Port] = {
    val app: HttpApp[Blocking, Throwable] =
      ZHttpInterpreter().toHttp(routes)(name => ZIO.succeed(s"Hello $name"))

    Resource.make(IO.(Server.start(8090, app))(_ => IO(8090))
  }
}
