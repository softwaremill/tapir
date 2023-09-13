package sttp.tapir.server.vertx

import io.vertx.ext.web.{RoutingContext}
import org.slf4j.{LoggerFactory}

import sttp.tapir.server.vertx.Helpers.RichResponse

/**
  * Common error handler implementation for all Vertx interpreter classes.
  * 
  * Ends the response of the current routing context safely.
  * 
  * @param rc 
  *   the routing context where the response shall be ended
  * @param ex
  *   exception that occurred during the interpreter call
  * @param performLogging
  *   whether to log an additional message with the exception or not
  */
trait VertxErrorHandler {
  def handleError(rc: RoutingContext, ex: Throwable, performLogging: Boolean = true): Unit = {
    if (performLogging) {
      LoggerFactory.getLogger(getClass.getName).error("Error while processing the request", ex)
    }
    if (rc.response().bytesWritten() > 0) rc.response().safeEndWait()
    rc.fail(ex)
  }
}
