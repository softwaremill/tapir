package sttp.tapir.server.tests

import cats.effect.IO
import cats.effect.kernel.Async
import cats.effect.syntax.all._
import cats.syntax.all._
import org.scalatest.matchers.should.Matchers._
import sttp.client3._
import sttp.monad.MonadError
import sttp.tapir._
import sttp.tapir.tests._

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.{Semaphore, TimeUnit}
import scala.concurrent.duration._

class ServerCancellationTests[F[_], OPTIONS, ROUTE](createServerTest: CreateServerTest[F, Any, OPTIONS, ROUTE])(implicit
    m: MonadError[F],
    async: Async[F]
) {
  import createServerTest._

  def tests(): List[Test] = List({
    val canceledSemaphore = new Semaphore(1)
    val canceled: AtomicBoolean = new AtomicBoolean(false)
    testServerLogic(
      endpoint
        .out(plainBody[String])
        .serverLogic { _ =>
          (m.eval(canceledSemaphore.acquire())) >> (async.sleep(15.seconds) >> pureResult("processing finished".asRight[Unit]))
            .onCancel(m.eval(canceled.set(true)) >> m.eval(canceledSemaphore.release()))
        },
      "Client cancelling request triggers cancellation on the server"
    ) { (backend, baseUri) =>
      val resp: IO[_] = basicRequest.get(uri"$baseUri").readTimeout(300.millis).send(backend)

      resp
        .map { case result =>
          fail(s"Expected cancellation, but received a result: $result")
        }
        .handleErrorWith {
          case _: SttpClientException.TimeoutException => // expected, this is how we trigged client-side cancellation
            IO(
              assert(
                canceledSemaphore.tryAcquire(30L, TimeUnit.SECONDS),
                "Timeout when waiting for cancellation to be handled as expected"
              )
            ) >>
              IO(assert(canceled.get(), "Cancellation expected, but not registered!"))
          case other =>
            IO(fail(s"TimeoutException expected, but got $other"))
        }
    }
  })
}
