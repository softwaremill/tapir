package sttp.tapir.server.vertx

import io.vertx.scala.core.Vertx
import io.vertx.scala.ext.web.{Route, Router}
import sttp.tapir.Endpoint
import sttp.tapir.server.{DecodeFailureHandler, ServerDefaults, ServerEndpoint}

import scala.concurrent.Future
import scala.reflect.ClassTag

class VertxServerBlockingInterpreter(vertx: Vertx) extends VertxServerInterpreter(vertx) {
  override def route[I, E, O](
      e: ServerEndpoint[I, E, O, Any, Future],
      decodeFailureHandler: Option[DecodeFailureHandler]
  ): Router => Route =
    e.blockingRoute(options.copy(decodeFailureHandler.getOrElse(ServerDefaults.decodeFailureHandler)))

  override def routeRecoverErrors[I, E <: Throwable, O](e: Endpoint[I, E, O, Any], fn: I => Future[O])(implicit
      eClassTag: ClassTag[E]
  ): Router => Route =
    e.blockingRouteRecoverErrors(fn)
}
