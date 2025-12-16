package sttp.tapir.server.tests

import cats.effect.IO
import cats.syntax.all._
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers._
import sttp.client4._
import sttp.model.StatusCode
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir._
import sttp.tapir.tests._

import scala.concurrent.duration._

class ServerGracefulShutdownTests[F[_], OPTIONS, ROUTE](createServerTest: CreateServerTest[F, Any, OPTIONS, ROUTE], sleeper: Sleeper[F])(
    implicit m: MonadError[F]
) extends EitherValues {
  import createServerTest._

  def tests(): List[Test] = List(
    testServerLogicWithStop(
      endpoint
        .out(plainBody[String])
        .serverLogic { _ =>
          sleeper.sleep(3.seconds).flatMap(_ => pureResult("processing finished".asRight[Unit]))
        },
      "Server waits for long-running request to complete within timeout",
      gracefulShutdownTimeout = Some(4.seconds)
    ) { (stopServer) => (backend, baseUri) =>
      (for {
        runningRequest <- basicRequest.get(uri"$baseUri").send(backend).start
        _ <- IO.sleep(1.second)
        runningStop <- stopServer.start
        result <- runningRequest.join.attempt
        _ <- runningStop.join
      } yield {
        result.value.isSuccess shouldBe true
      })
    },
    testServerLogicWithStop(
      endpoint
        .out(plainBody[String])
        .serverLogic { _ =>
          sleeper.sleep(4.seconds).flatMap(_ => pureResult("processing finished".asRight[Unit]))
        },
      "Server rejects requests with 503 during shutdown",
      gracefulShutdownTimeout = Some(6.seconds)
    ) { (stopServer) => (backend, baseUri) =>
      (for {
        runningRequest <- basicRequest.get(uri"$baseUri").send(backend).start
        _ <- IO.sleep(1.second)
        runningStop <- stopServer.start
        _ <- IO.sleep(1.seconds)
        rejected <- basicRequest.get(uri"$baseUri").send(backend).attempt
        firstResult <- runningRequest.join.attempt
        _ <- runningStop.join
      } yield {
        (rejected.value.code shouldBe StatusCode.ServiceUnavailable): Unit
        firstResult.value.isSuccess shouldBe true
      })
    }
  )
}
