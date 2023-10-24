package sttp.tapir.server.tests

import cats.effect.IO
import cats.effect.kernel.Async
import cats.effect.syntax.all._
import cats.syntax.all._
import org.scalatest
import org.scalatest.compatible.Assertion
import org.scalatest.matchers.should.Matchers._
import sttp.client3._
import sttp.monad.MonadError
import sttp.tapir._
import sttp.tapir.tests._

import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.duration._

class ServerCancellationTests[F[_], OPTIONS, ROUTE](createServerTest: CreateServerTest[F, Any, OPTIONS, ROUTE])(implicit
    m: MonadError[F],
    async: Async[F]
) {
  import createServerTest._

  def tests(): List[Test] = List({
    val canceled: AtomicBoolean = new AtomicBoolean(false)
    testServerLogic(
      endpoint
        .out(plainBody[String])
        .serverLogic { _ =>
          (async.sleep(15.seconds) >> pureResult("processing finished".asRight)).onCancel(m.eval(canceled.set(true)))
        },
      "Client cancelling request triggers cancellation on the server"
    ) { (backend, baseUri) =>
      val resp: IO[Assertion] = basicRequest.get(uri"$baseUri").send(backend).timeout(300.millis).map { case result =>
        fail(s"Expected cancellation, but received a result: $result")
      }

      resp.handleErrorWith {
        case _: TimeoutException =>
          IO(assert(canceled.get(), "Cancellation expected, but not registered!"))
        case other =>
          IO(fail(s"TimeoutException expected, but got $other"))
      }
    }
  })
}
