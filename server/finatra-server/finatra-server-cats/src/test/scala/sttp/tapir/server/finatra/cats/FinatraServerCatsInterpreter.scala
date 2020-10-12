package sttp.tapir.server.finatra.cats

import cats.data.NonEmptyList
import cats.effect.{ContextShift, IO, Resource, Timer}
import sttp.tapir.Endpoint
import sttp.tapir.server.finatra.{FinatraRoute, FinatraServerInterpreter, FinatraServerOptions}
import sttp.tapir.server.tests.ServerInterpreter
import sttp.tapir.server.{DecodeFailureHandler, ServerDefaults, ServerEndpoint}
import sttp.tapir.tests.Port

import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

class FinatraServerCatsInterpreter extends ServerInterpreter[IO, Any, FinatraRoute] {
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  implicit val contextShift: ContextShift[IO] = IO.contextShift(ec)
  implicit val timer: Timer[IO] = IO.timer(ec)

  override def route[I, E, O](
      e: ServerEndpoint[I, E, O, Any, IO],
      decodeFailureHandler: Option[DecodeFailureHandler] = None
  ): FinatraRoute = {
    implicit val serverOptions: FinatraServerOptions =
      FinatraServerOptions.default.copy(decodeFailureHandler = decodeFailureHandler.getOrElse(ServerDefaults.decodeFailureHandler))
    e.toRoute
  }

  override def routeRecoverErrors[I, E <: Throwable, O](e: Endpoint[I, E, O, Any], fn: I => IO[O])(implicit
      eClassTag: ClassTag[E]
  ): FinatraRoute = e.toRouteRecoverErrors(fn)

  override def server(routes: NonEmptyList[FinatraRoute], port: Port): Resource[IO, Unit] = FinatraServerInterpreter.server(routes, port)
}
