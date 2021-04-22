package sttp.tapir.server.tests

import cats.effect.IO
import cats.implicits._
import org.scalatest.matchers.should.Matchers._
import sttp.client3.{SttpBackend, _}
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.metrics.Metric
import sttp.tapir.server.interceptor.metrics.{MetricsEndpointInterceptor, MetricsRequestInterceptor}
import sttp.tapir.server.tests.ServerMetricsTest.Counter
import sttp.tapir.tests.TestUtil.inputStreamToByteArray
import sttp.tapir.tests.{Test, _}

import java.io.{ByteArrayInputStream, InputStream}

class ServerMetricsTest[F[_], ROUTE, B](
    backend: SttpBackend[IO, Any],
    createServerTest: CreateServerTest[F, Any, ROUTE, B]
)(implicit m: MonadError[F]) {
  import createServerTest._

  def tests(): List[Test] = List(
    {
      val reqCounter = Metric[Counter](new Counter()).onRequest { (_, _, c) => c.++() }
      val resCounter = Metric[Counter](new Counter()).onResponse { (_, _, _, _, c) => c.++() }

      val metrics = new MetricsRequestInterceptor[F, B](List(reqCounter, resCounter), Seq.empty)

      testServer(in_json_out_json.name("metrics"), metricsInterceptor = metrics.some)(f =>
        (if (f.fruit == "apple") Right(f) else Left(())).unit
      ) { baseUri =>
        basicRequest // onDecodeSuccess path
          .post(uri"$baseUri/api/echo")
          .body("""{"fruit":"apple","amount":1}""")
          .send(backend)
          .map { r =>
            r.body shouldBe Right("""{"fruit":"apple","amount":1}""")
            reqCounter.metric.value shouldBe 1
            resCounter.metric.value shouldBe 1
          } >> basicRequest // onDecodeFailure path
          .post(uri"$baseUri/api/echo")
          .body("""{"invalid":"body",}""")
          .send(backend)
          .map { _ =>
            Thread.sleep(100)
            reqCounter.metric.value shouldBe 2
            resCounter.metric.value shouldBe 2
          }
      }
    }, {
      val resCounter = Metric[Counter](new Counter()).onResponse { (_, _, _, _, c) => c.++() }
      val metrics = new MetricsRequestInterceptor[F, B](List(resCounter), Seq.empty)

      testServer(in_input_stream_out_input_stream.name("metrics"), metricsInterceptor = metrics.some)(is =>
        (new ByteArrayInputStream(inputStreamToByteArray(is)): InputStream).asRight[Unit].unit
      ) { baseUri =>
        basicRequest
          .post(uri"$baseUri/api/echo")
          .body("okoń")
          .send(backend)
          .map { r =>
            r.body shouldBe Right("okoń")
            resCounter.metric.value shouldBe 1
          }
      }
    }, {
      val resCounter = Metric[Counter](new Counter()).onResponse { (_, _, _, _, c) => c.++() }
      val metrics = new MetricsRequestInterceptor[F, B](List(resCounter), Seq.empty)

      testServer(in_empty_out_empty.name("metrics"), metricsInterceptor = metrics.some)(_ => ().asRight[Unit].unit) { baseUri =>
        basicRequest
          .post(uri"$baseUri/api/empty")
          .send(backend)
          .map { r =>
            r.body shouldBe Right("")
            resCounter.metric.value shouldBe 1
          }
      }
    }, {
      val reqCounter = Metric[Counter](new Counter()).onRequest { (_, _, c) => c.++() }
      val resCounter = Metric[Counter](new Counter()).onResponse { (_, _, _, _, c) => c.++() }
      val metrics = new MetricsRequestInterceptor[F, B](List(reqCounter, resCounter), Seq.empty)

      testServer(in_empty_out_empty.name("metrics on exception"), metricsInterceptor = metrics.some)(_ =>
        throw new RuntimeException("Ups")
      ) { baseUri =>
        basicRequest
          .post(uri"$baseUri/api/empty")
          .send(backend)
          .map { _ =>
            reqCounter.metric.value shouldBe 1
            resCounter.metric.value shouldBe 1
          }
      }
    }
  )
}

object ServerMetricsTest {
  class Counter(var value: Int = 0) {
    def ++(): Unit = value += 1
  }
}
