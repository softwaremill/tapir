package sttp.tapir.server.tests

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import sttp.tapir.Endpoint
import sttp.tapir.server.interceptor.decodefailure.DecodeFailureHandler
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.tests.Port

import scala.reflect.ClassTag

trait TestServerInterpreter[F[_], +R, ROUTE] {
  def route[I, E, O](e: ServerEndpoint[I, E, O, R, F], decodeFailureHandler: Option[DecodeFailureHandler] = None): ROUTE
  def routeRecoverErrors[I, E <: Throwable, O](e: Endpoint[I, E, O, R], fn: I => F[O])(implicit eClassTag: ClassTag[E]): ROUTE
  def server(routes: NonEmptyList[ROUTE]): Resource[IO, Port]
}
