package sttp.tapir.server.finatra.cats

import cats.data.NonEmptyList
import cats.effect.std.Dispatcher
import cats.effect.{IO, Resource}
import sttp.tapir.Endpoint
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.finatra.{FinatraContent, FinatraRoute, FinatraTestServerInterpreter}
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureHandler, DefaultDecodeFailureHandler}
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.tests.Port

import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

class FinatraCatsTestServerInterpreter(dispatcher: Dispatcher[IO]) extends TestServerInterpreter[IO, Any, FinatraRoute, FinatraContent] {
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  override def route[I, E, O](
      e: ServerEndpoint[I, E, O, Any, IO],
      decodeFailureHandler: Option[DecodeFailureHandler] = None,
      metricsInterceptor: Option[MetricsRequestInterceptor[IO, FinatraContent]] = None
  ): FinatraRoute = {
    val serverOptions: FinatraCatsServerOptions[IO] =
      FinatraCatsServerOptions.customInterceptors(
        dispatcher,
        decodeFailureHandler = decodeFailureHandler.getOrElse(DefaultDecodeFailureHandler.handler)
      )
    FinatraCatsServerInterpreter[IO](serverOptions).toRoute(e)
  }

  override def routeRecoverErrors[I, E <: Throwable, O](e: Endpoint[I, E, O, Any], fn: I => IO[O])(implicit
      eClassTag: ClassTag[E]
  ): FinatraRoute = FinatraCatsServerInterpreter[IO](dispatcher).toRouteRecoverErrors(e)(fn)

  override def server(routes: NonEmptyList[FinatraRoute]): Resource[IO, Port] = FinatraTestServerInterpreter.server(routes)
}
