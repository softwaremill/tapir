package sttp.tapir.server.vertx.encoders

import io.vertx.core.Future
import io.vertx.ext.web.RoutingContext
import sttp.tapir.model.ServerResponse

import scala.util.control.NonFatal

object VertxOutputEncoders {
  private[vertx] def apply(serverResponse: ServerResponse[RoutingContext => Future[Void]]): RoutingContext => Future[Void] = { rc =>
    val resp = rc.response
    try {
      resp.setStatusCode(serverResponse.code.code)
      serverResponse.headers.foreach { h => resp.headers.add(h.name, h.value) }
      serverResponse.body match {
        case Some(responseHandler) => responseHandler(rc)
        case None                  => resp.end()
      }
    } catch {
      case NonFatal(e) => Future.failedFuture(e)
    }
  }
}
