package sttp.tapir.server.tests

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.CustomiseInterceptors
import sttp.tapir.tests.Port

trait TestServerInterpreter[F[_], +R, OPTIONS, ROUTE] {
  protected type Interceptors = CustomiseInterceptors[F, OPTIONS] => CustomiseInterceptors[F, OPTIONS]

  def route(e: ServerEndpoint[R, F]): ROUTE = route(List(e), (ci: CustomiseInterceptors[F, OPTIONS]) => ci)

  def route(e: ServerEndpoint[R, F], interceptors: Interceptors): ROUTE = route(List(e), interceptors)

  def route(es: List[ServerEndpoint[R, F]], interceptors: Interceptors = identity): ROUTE

  def server(routes: NonEmptyList[ROUTE]): Resource[IO, Port]
}
