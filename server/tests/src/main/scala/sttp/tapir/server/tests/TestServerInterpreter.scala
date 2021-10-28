package sttp.tapir.server.tests

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import sttp.tapir.Endpoint
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.decodefailure.DecodeFailureHandler
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.tests.Port

import scala.reflect.ClassTag

trait TestServerInterpreter[F[_], +R, ROUTE] {
  def route[A, U, I, E, O](
      e: ServerEndpoint[A, U, I, E, O, R, F],
      decodeFailureHandler: Option[DecodeFailureHandler] = None,
      metricsInterceptor: Option[MetricsRequestInterceptor[F]] = None
  ): ROUTE
  def route[A, U, I, E, O](es: List[ServerEndpoint[A, U, I, E, O, R, F]]): ROUTE
  def server(routes: NonEmptyList[ROUTE]): Resource[IO, Port]
}
