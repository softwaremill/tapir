package sttp.tapir.server.vertx

import io.vertx.core.Promise
import io.vertx.core.http.HttpServerResponse

import org.slf4j.{LoggerFactory}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.{Future, ExecutionContext}

object Helpers {

  /** 
   * Helper class that implements safer ending of a http server response
   */
  implicit class RichResponse(response: HttpServerResponse) {

    /**
     * Ends the response if it hasn't ended yet
     * @return
     *   A future that is completed when the response has been ended
     */
    def safeEnd(): io.vertx.core.Future[Void] = {
      if (!response.ended()) response.end()
      else Promise.promise().future()
    }

    /** Ends the response if it hasn't ended yet and waits for it to be executed
      *
      * @param duration
      *   maximum waiting time
      */
    def safeEndWait(duration: FiniteDuration = 2.seconds): Unit = {
      import ExecutionContext.Implicits.global
      if (!response.ended()) {
        try {
          Await.result(
            Future {
              response.end().toCompletionStage().toCompletableFuture().get()
            },
            duration
          ): Unit
        } catch {
          case t: Throwable =>
            LoggerFactory.getLogger(getClass.getName).error("Caught exception while processing end", t)
        }
      }
    }
  }
}
