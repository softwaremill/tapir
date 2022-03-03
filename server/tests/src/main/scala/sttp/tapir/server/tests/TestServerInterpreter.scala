package sttp.tapir.server.tests

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.CustomInterceptors
import sttp.tapir.tests.Port

trait TestServerInterpreter[F[_], +R, OPTIONS, ROUTE] {
  protected type Interceptors = CustomInterceptors[F, OPTIONS] => CustomInterceptors[F, OPTIONS]

  def route(e: ServerEndpoint[R, F]): ROUTE = route(List(e), (ci: CustomInterceptors[F, OPTIONS]) => ci)

  def route(e: ServerEndpoint[R, F], interceptors: Interceptors): ROUTE = route(List(e), interceptors)

  def route(es: List[ServerEndpoint[R, F]], interceptors: Interceptors = identity): ROUTE

  def server(routes: NonEmptyList[ROUTE]): Resource[IO, Port]
}
