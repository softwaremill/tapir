package sttp.tapir.server.vertx

import io.vertx.core.Promise
import io.vertx.core.http.HttpServerResponse

import scala.concurrent.Await
import scala.concurrent.Future

object Helpers {

  /** Helper class that implements safer ending of a http server response
    */
  implicit class RichResponse(response: HttpServerResponse) {

    /** Ends the response if it hasn't ended yet
      * @return
      *   A future that is completed when the response has been ended
      */
    def safeEnd(): io.vertx.core.Future[Void] = {
      if (!response.ended()) response.end()
      else Promise.promise().future()
    }

    /** Ends the response if it hasn't ended yet and waits for it to be executed
      */
    def safeEndWait(): Unit = {
      import VertxFutureServerInterpreter.VertxFutureToScalaFuture
      if (!response.ended()) {
        Await.result(response.end().asScala, VertxServerOptions.safeEndWaitTime): Unit
      }
    }
  }
}
