package sttp.tapir.server.tests

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.CustomiseInterceptors
import sttp.tapir.tests.Port
import scala.concurrent.duration.FiniteDuration

case class TestServer(port: Port, stop: IO[Unit])

trait TestServerInterpreter[F[_], +R, OPTIONS, ROUTE] {
  type StopServer = IO[Unit]

  protected type Interceptors = CustomiseInterceptors[F, OPTIONS] => CustomiseInterceptors[F, OPTIONS]

  def route(e: ServerEndpoint[R, F]): ROUTE = route(List(e), (ci: CustomiseInterceptors[F, OPTIONS]) => ci)

  def route(e: ServerEndpoint[R, F], interceptors: Interceptors): ROUTE = route(List(e), interceptors)

  def route(es: List[ServerEndpoint[R, F]], interceptors: Interceptors = identity): ROUTE

  def server(routes: NonEmptyList[ROUTE], stopTimeout: Option[FiniteDuration] = None): Resource[IO, NettyTestServer]

  /** Exposes additional `stop` effect, which allows stopping the server inside your test. It will be called after the test anyway (assuming
    * idempotency), but may be useful for some cases where tests need to check specific behavior like returning 503s during shutdown.
    */
  def serverWithStop(routes: NonEmptyList[ROUTE], gracefulShutdownTimeout: Option[FiniteDuration] = None): Resource[IO, (Port, IO[Unit])] =
    server(routes).map(port => (port, IO.unit))
}
