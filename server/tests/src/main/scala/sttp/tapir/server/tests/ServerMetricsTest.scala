package sttp.tapir.server.tests

import cats.effect.IO
import cats.implicits._
import org.scalatest.matchers.should.Matchers._
import sttp.client3.{SttpBackend, _}
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.metrics.Metric
import sttp.tapir.server.interceptor.metrics.MetricsInterceptor
import sttp.tapir.server.interpreter.ServerResponseListener
import sttp.tapir.tests.{Test, _}

class ServerMetricsTest[F[_], ROUTE, B](
    backend: SttpBackend[IO, Any],
    createServerTest: CreateServerTest[F, Any, ROUTE, B]
)(implicit m: MonadError[F], responseListener: ServerResponseListener[F, B]) {
  import createServerTest._

  class Counter(var value: Int = 0) {
    def ++(): Unit = value += 1
  }

  def tests(): List[Test] = List(
    {
      val reqCounter = Metric[F, Counter](new Counter()).onRequest { (_, _, c) => m.unit(c.++()) }
      val resCounter = Metric[F, Counter](new Counter()).onResponse { (_, _, _, c) => m.unit(c.++()) }

      val metrics = new MetricsInterceptor[F, B](List(reqCounter, resCounter), Seq.empty)

      testServer(in_json_out_json.name("metrics"), interceptors = List(metrics))(f =>
        (if (f.fruit == "apple") Right(f) else Left(())).unit
      ) { baseUri =>
        basicRequest // onDecodeSuccess path
          .post(uri"$baseUri/api/echo")
          .body("""{"fruit":"apple","amount":1}""")
          .send(backend)
          .map { _ =>
            reqCounter.metric.value shouldBe 1
            resCounter.metric.value shouldBe 1
          } >> basicRequest // onDecodeFailure path
          .post(uri"$baseUri/api/echo")
          .body("""{"invalid":"body",}""")
          .send(backend)
          .map { _ =>
            reqCounter.metric.value shouldBe 2
            resCounter.metric.value shouldBe 2
          }
      }
    }
  )
}
