package sttp.tapir.server.vertx

import io.vertx.core.Vertx
import io.vertx.ext.web.{Route, Router}
import sttp.tapir.Endpoint
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureHandler, DefaultDecodeFailureHandler}

import scala.concurrent.Future
import scala.reflect.ClassTag

class VertxTestServerBlockingInterpreter(vertx: Vertx) extends VertxTestServerInterpreter(vertx) {
  override def route[I, E, O](
      e: ServerEndpoint[I, E, O, Any, Future],
      decodeFailureHandler: Option[DecodeFailureHandler]
  ): Router => Route = {
    implicit val options: VertxFutureServerOptions = VertxFutureServerOptions.customInterceptors(decodeFailureHandler =
      decodeFailureHandler.getOrElse(DefaultDecodeFailureHandler.handler)
    )
    VertxFutureServerInterpreter.blockingRoute(e)
  }

  override def routeRecoverErrors[I, E <: Throwable, O](e: Endpoint[I, E, O, Any], fn: I => Future[O])(implicit
      eClassTag: ClassTag[E]
  ): Router => Route =
    VertxFutureServerInterpreter.blockingRouteRecoverErrors(e)(fn)
}
