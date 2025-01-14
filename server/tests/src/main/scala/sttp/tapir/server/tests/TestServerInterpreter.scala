package sttp.tapir.server.tests

import cats.data.NonEmptyList
import cats.effect.{Deferred, IO, Resource}
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.CustomiseInterceptors
import sttp.tapir.tests._

import scala.concurrent.duration.FiniteDuration

trait TestServerInterpreter[F[_], +R, OPTIONS, ROUTE] {

  protected type Interceptors = CustomiseInterceptors[F, OPTIONS] => CustomiseInterceptors[F, OPTIONS]

  def route(e: ServerEndpoint[R, F]): ROUTE = route(List(e), (ci: CustomiseInterceptors[F, OPTIONS]) => ci)

  def route(e: ServerEndpoint[R, F], interceptors: Interceptors): ROUTE = route(List(e), interceptors)

  def route(es: List[ServerEndpoint[R, F]], interceptors: Interceptors = identity): ROUTE

  def serverWithStop(
      routes: NonEmptyList[ROUTE],
      gracefulShutdownTimeout: Option[FiniteDuration] = None
  ): Resource[IO, (Port, KillSwitch)] = for {
    stopSignal <- Resource.eval(Deferred[IO, Unit])
    portValue <- Resource.eval(Deferred[IO, Port])
    _ <- server(routes, gracefulShutdownTimeout).evalTap(portValue.complete).surround(stopSignal.get).background
    port <- Resource.eval(portValue.get)
  } yield (port, stopSignal.complete(()).void)

  def server(routes: NonEmptyList[ROUTE], gracefulShutdownTimeout: Option[FiniteDuration] = None): Resource[IO, Port]
}
