package sttp.tapir.server.http4s

import cats.data.{Kleisli, NonEmptyList}
import cats.effect.{Concurrent, ContextShift, IO, Resource, Timer}
import cats.syntax.all._
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.syntax.kleisli._
import org.http4s.{HttpRoutes, Request, Response}
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.Endpoint
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureHandler, DefaultDecodeFailureHandler}
import sttp.tapir.server.interceptor.exception.DefaultExceptionHandler
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.tests.Port

import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

class Http4sTestServerInterpreter
    extends TestServerInterpreter[IO, Fs2Streams[IO] with WebSockets, HttpRoutes[IO], Http4sResponseBody[IO]] {
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  implicit val contextShift: ContextShift[IO] = IO.contextShift(ec)
  implicit val timer: Timer[IO] = IO.timer(ec)

  override def route[I, E, O](
      e: ServerEndpoint[I, E, O, Fs2Streams[IO] with WebSockets, IO],
      decodeFailureHandler: Option[DecodeFailureHandler] = None,
      metricsInterceptor: Option[MetricsRequestInterceptor[IO, Http4sResponseBody[IO]]] = None
  ): HttpRoutes[IO] = {
    val serverOptions: Http4sServerOptions[IO, IO] = Http4sServerOptions
      .customInterceptors(
        metricsInterceptor = metricsInterceptor,
        exceptionHandler = Some(DefaultExceptionHandler),
        serverLog = Some(Http4sServerOptions.Log.defaultServerLog),
        decodeFailureHandler = decodeFailureHandler.getOrElse(DefaultDecodeFailureHandler.handler)
      )
    Http4sServerInterpreter(serverOptions).toRoutes(e)
  }

  override def routeRecoverErrors[I, E <: Throwable, O](e: Endpoint[I, E, O, Fs2Streams[IO] with WebSockets], fn: I => IO[O])(implicit
      eClassTag: ClassTag[E]
  ): HttpRoutes[IO] = {
    Http4sServerInterpreter[IO]().toRouteRecoverErrors(e)(fn)
  }

  override def server(routes: NonEmptyList[HttpRoutes[IO]]): Resource[IO, Port] = {
    val service: Kleisli[IO, Request[IO], Response[IO]] = routes.reduceK.orNotFound

    BlazeServerBuilder[IO](ExecutionContext.global)
      .bindHttp(0, "localhost")
      .withHttpApp(service)
      .resource
      .map(_.address.getPort)
  }
}
