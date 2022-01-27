package sttp.tapir.server.tests

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.decodefailure.DecodeFailureHandler
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.tests.Port

trait TestServerInterpreter[F[_], +R, ROUTE] {
  def route(
      e: ServerEndpoint[R, F],
      decodeFailureHandler: Option[DecodeFailureHandler] = None,
      metricsInterceptor: Option[MetricsRequestInterceptor[F]] = None
  ): ROUTE
  def route(es: List[ServerEndpoint[R, F]]): ROUTE
  def server(routes: NonEmptyList[ROUTE]): Resource[IO, Port]
}
