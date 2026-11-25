package sttp.tapir.server.tests

import cats.effect.IO
import cats.effect.kernel.Async
import cats.effect.syntax.all._
import cats.syntax.all._
import org.scalatest.matchers.should.Matchers._
import sttp.client4._
import sttp.monad.MonadError
import sttp.tapir._
import sttp.tapir.tests._

import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.{CountDownLatch, Semaphore, TimeUnit}
import scala.concurrent.duration._

class ServerCancellationTests[F[_], OPTIONS, ROUTE](createServerTest: CreateServerTest[F, Any, OPTIONS, ROUTE])(implicit
    m: MonadError[F],
    async: Async[F]
) {
  import createServerTest._

  def tests(): List[Test] = List(
    {
      // The semaphore starts with no permits; the only `release` happens in the server logic's cancellation finalizer,
      // so the test's `tryAcquire` below blocks until cancellation has been handled.
      val canceledSemaphore = new Semaphore(0)
      val canceled: AtomicBoolean = new AtomicBoolean(false)
      testServerLogic(
        endpoint
          .out(plainBody[String])
          .serverLogic { _ =>
            (async.sleep(15.seconds) >> pureResult("processing finished".asRight[Unit]))
              .onCancel(m.eval(canceled.set(true)) >> m.eval(canceledSemaphore.release()))
          },
        "Client cancelling request triggers cancellation on the server"
      ) { (backend, baseUri) =>
        val resp: IO[?] = basicRequest.get(uri"$baseUri").readTimeout(300.millis).send(backend)

        resp
          .map(result => fail(s"Expected cancellation, but received a result: $result"))
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
    }, {
      val started = new CountDownLatch(1)
      val canceledSemaphore = new Semaphore(0)
      testServerLogic(
        endpoint.post
          .out(plainBody[String])
          .serverLogic { _ =>
            (m.eval(started.countDown()) >> async.sleep(15.seconds) >> pureResult("processing finished".asRight[Unit]))
              .onCancel(m.eval(canceledSemaphore.release()))
          },
        "Client disconnecting while sending the request body triggers cancellation on the server"
      ) { (_, baseUri) =>
        // the body isn't read by the server logic, and isn't sent in full
        val requestStart = "POST / HTTP/1.1\r\nHost: localhost\r\nContent-Length: 100\r\n\r\nhello"
        for {
          startedBeforeDisconnect <- rawSocket(baseUri.port.get).use { socket =>
            sendRaw(socket, requestStart.getBytes(StandardCharsets.US_ASCII)) >> IO.blocking(started.await(10, TimeUnit.SECONDS))
          }
          canceled <- IO.blocking(canceledSemaphore.tryAcquire(30L, TimeUnit.SECONDS))
        } yield {
          assert(startedBeforeDisconnect, "The server logic didn't start")
          assert(canceled, "Timeout when waiting for cancellation to be handled as expected")
        }
      }
    }
  )
}
