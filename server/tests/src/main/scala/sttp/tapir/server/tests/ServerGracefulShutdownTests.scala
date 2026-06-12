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

import java.util.concurrent.{CountDownLatch, TimeUnit}
import scala.concurrent.duration._

class ServerGracefulShutdownTests[F[_], OPTIONS, ROUTE](createServerTest: CreateServerTest[F, Any, OPTIONS, ROUTE], sleeper: Sleeper[F])(
    implicit m: MonadError[F]
) extends EitherValues {
  import createServerTest._

  def tests(): List[Test] = List(
    {
      // Released by the server logic once it starts handling the request, so the test triggers shutdown only after
      // the request is genuinely in-flight. Relying on a fixed sleep instead races under load: the stop could begin
      // before the request reaches the server, which then aborts it and fails the test.
      // The graceful-shutdown timeout is kept well above the request duration: the server force-closes in-flight
      // requests once the timeout elapses, and a tight margin (e.g. 3s of work vs a 4s timeout) flakes on a loaded
      // CI runner where the sleep and response write drift past it. A larger timeout doesn't slow the happy path -
      // the stop returns as soon as the request's connection closes, not when the timeout elapses.
      val requestReceived = new CountDownLatch(1)
      testServerLogicWithStop(
        endpoint
          .out(plainBody[String])
          .serverLogic { _ =>
            m.eval(requestReceived.countDown())
              .flatMap(_ => sleeper.sleep(2.seconds))
              .flatMap(_ => pureResult("processing finished".asRight[Unit]))
          },
        "Server waits for long-running request to complete within timeout",
        gracefulShutdownTimeout = Some(10.seconds)
      ) { (stopServer) => (backend, baseUri) =>
        (for {
          runningRequest <- basicRequest.get(uri"$baseUri").send(backend).start
          requestArrived <- IO.blocking(requestReceived.await(10, TimeUnit.SECONDS))
          runningStop <- stopServer.start
          result <- runningRequest.join.attempt
          _ <- runningStop.join
        } yield {
          (requestArrived shouldBe true): Unit
          result.value.isSuccess shouldBe true
        })
      }
    }, {
      val requestReceived = new CountDownLatch(1)
      testServerLogicWithStop(
        endpoint
          .out(plainBody[String])
          .serverLogic { _ =>
            m.eval(requestReceived.countDown())
              .flatMap(_ => sleeper.sleep(4.seconds))
              .flatMap(_ => pureResult("processing finished".asRight[Unit]))
          },
        "Server rejects requests with 503 during shutdown",
        gracefulShutdownTimeout = Some(10.seconds)
      ) { (stopServer) => (backend, baseUri) =>
        (for {
          runningRequest <- basicRequest.get(uri"$baseUri").send(backend).start
          requestArrived <- IO.blocking(requestReceived.await(10, TimeUnit.SECONDS))
          runningStop <- stopServer.start
          _ <- IO.sleep(1.seconds) // give shutdown time to start rejecting new requests
          rejected <- basicRequest.get(uri"$baseUri").send(backend).attempt
          firstResult <- runningRequest.join.attempt
          _ <- runningStop.join
        } yield {
          (requestArrived shouldBe true): Unit
          (rejected.value.code shouldBe StatusCode.ServiceUnavailable): Unit
          firstResult.value.isSuccess shouldBe true
        })
      }
    }
  )
}
