package sttp.tapir.server.tests

import cats.effect.IO
import cats.implicits._
import org.scalatest.concurrent.Eventually.eventually
import org.scalatest.matchers.should.Matchers._
import sttp.client3.{SttpBackend, _}
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.metrics.{EndpointMetric, Metric}
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.server.tests.ServerMetricsTest._
import sttp.tapir.tests.TestUtil.inputStreamToByteArray
import sttp.tapir.tests.{Test, _}

import java.io.{ByteArrayInputStream, InputStream}
import java.util.concurrent.atomic.AtomicInteger

class ServerMetricsTest[F[_], ROUTE, B](
    backend: SttpBackend[IO, Any],
    createServerTest: CreateServerTest[F, Any, ROUTE, B]
)(implicit m: MonadError[F]) {
  import createServerTest._

  def tests(): List[Test] = List(
    {
      val reqCounter = newRequestCounter[F]
      val resCounter = newResponseCounter[F]
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
            reqCounter.metric.value.get() shouldBe 1
            resCounter.metric.value.get() shouldBe 1
          } >> basicRequest // onDecodeFailure path
          .post(uri"$baseUri/api/echo")
          .body("""{"invalid":"body",}""")
          .send(backend)
          .map { _ =>
            eventually {
              reqCounter.metric.value.get() shouldBe 2
              resCounter.metric.value.get() shouldBe 2
            }
          }
      }
    }, {
      val resCounter = newResponseCounter[F]
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
            eventually {
              resCounter.metric.value.get() shouldBe 1
            }
          }
      }
    }, {
      val resCounter = newResponseCounter[F]
      val metrics = new MetricsRequestInterceptor[F, B](List(resCounter), Seq.empty)

      testServer(in_root_path.name("metrics"), metricsInterceptor = metrics.some)(_ => ().asRight[Unit].unit) { baseUri =>
        basicRequest
          .get(uri"$baseUri")
          .send(backend)
          .map { r =>
            r.body shouldBe Right("")
            eventually {
              resCounter.metric.value.get() shouldBe 1
            }
          }
      }
    }, {
      val reqCounter = newRequestCounter[F]
      val resCounter = newResponseCounter[F]
      val metrics = new MetricsRequestInterceptor[F, B](List(reqCounter, resCounter), Seq.empty)

      testServer(in_root_path.name("metrics on exception"), metricsInterceptor = metrics.some) { _ =>
        Thread.sleep(100)
        throw new RuntimeException("Ups")
      } { baseUri =>
        basicRequest
          .get(uri"$baseUri")
          .send(backend)
          .map { _ =>
            eventually {
              reqCounter.metric.value.get() shouldBe 1
              resCounter.metric.value.get() shouldBe 1
            }
          }
      }
    }
  )
}

object ServerMetricsTest {
  case class Counter(value: AtomicInteger = new AtomicInteger(0)) {
    def ++(): Unit = value.incrementAndGet()
  }

  def newRequestCounter[F[_]]: Metric[F, Counter] =
    Metric[F, Counter](new Counter(), onRequest = { (_, c, m) => m.unit(EndpointMetric().onRequest { _ => m.unit(c.++()) }) })

  def newResponseCounter[F[_]]: Metric[F, Counter] =
    Metric[F, Counter](new Counter(), onRequest = { (_, c, m) => m.unit(EndpointMetric().onResponse { (_, _) => m.unit(c.++()) }) })
}
