package sttp.tapir.server.vertx

import io.vertx.scala.core.Vertx
import io.vertx.scala.ext.web.{Route, Router}
import sttp.capabilities.Effect
import sttp.tapir.Endpoint
import sttp.tapir.server.{DecodeFailureHandler, ServerDefaults, ServerEndpoint}

import scala.concurrent.Future
import scala.reflect.ClassTag

class VertxTestServerBlockingInterpreter(vertx: Vertx) extends VertxTestServerInterpreter(vertx) {
  override def route[I, E, O](
      e: ServerEndpoint[I, E, O, Effect[Future], Future],
      decodeFailureHandler: Option[DecodeFailureHandler]
  ): Router => Route =
    VertxServerInterpreter.blockingRoute(e)(options.copy(decodeFailureHandler.getOrElse(ServerDefaults.decodeFailureHandler)))

  override def routeRecoverErrors[I, E <: Throwable, O](e: Endpoint[I, E, O, Effect[Future]], fn: I => Future[O])(implicit
      eClassTag: ClassTag[E]
  ): Router => Route =
    VertxServerInterpreter.blockingRouteRecoverErrors(e)(fn)
}
