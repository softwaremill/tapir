package sttp.tapir.server.vertx.encoders

import io.vertx.ext.web.RoutingContext
import sttp.tapir.model.ServerResponse

import scala.util.{Failure, Success, Try}

object VertxOutputEncoders {
  private[vertx] def apply(serverResponse: ServerResponse[RoutingContext => Unit]): RoutingContext => Unit = { rc =>
    val resp = rc.response
    Try {
      resp.setStatusCode(serverResponse.code.code)
      serverResponse.headers.foreach { h => resp.headers.add(h.name, h.value) }
      serverResponse.body match {
        case Some(responseHandler) => responseHandler(rc)
        case None => resp.end()
      }
    } match {
      case Success(_) => ()
      case Failure(ex) =>
        // send 500
        resp.setStatusCode(500)
        resp.headers.clear()
        resp.end()
    }
  }
}
