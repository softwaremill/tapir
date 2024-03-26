package sttp.tapir.server.tests

import cats.implicits._
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.Eventually.eventually
import org.scalatest.matchers.should.Matchers._
import sttp.client3._
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.server.interceptor.CustomiseInterceptors
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.server.metrics.{EndpointMetric, Metric}
import sttp.tapir.server.tests.ServerMetricsTest._
import sttp.tapir.tests.Basic.{in_input_stream_out_input_stream, in_json_out_json, in_root_path}
import sttp.tapir.tests.Test
import sttp.tapir.tests.TestUtil.inputStreamToByteArray

import java.io.{ByteArrayInputStream, InputStream}
import java.util.concurrent.atomic.AtomicInteger

class ServerMetricsTest[F[_], OPTIONS, ROUTE](createServerTest: CreateServerTest[F, Any, OPTIONS, ROUTE])(implicit m: MonadError[F]) {
  import createServerTest._

  // increase the patience for `eventually` for slow CI tests
  implicit val patienceConfig: Eventually.PatienceConfig = Eventually.PatienceConfig(
    timeout = org.scalatest.time.Span(15, org.scalatest.time.Seconds),
    interval = org.scalatest.time.Span(150, org.scalatest.time.Millis)
  )

  def tests(): List[Test] = List(
    {
      val reqCounter = newRequestCounter[F]
      val resCounter = newResponseCounter[F]
      val metrics = new MetricsRequestInterceptor[F](List(reqCounter, resCounter), Seq.empty)

      testServer(
        in_json_out_json.name("metrics"),
        interceptors = (ci: CustomiseInterceptors[F, OPTIONS]) => ci.metricsInterceptor(metrics)
      )(f => (if (f.fruit == "apple") Right(f) else Left(())).unit) { (backend, baseUri) =>
        basicRequest // onDecodeSuccess path
          .post(uri"$baseUri/api/echo")
          .body("""{"fruit":"apple","amount":1}""")
          .send(backend)
          .map { r =>
            r.body shouldBe Right("""{"fruit":"apple","amount":1}""")
            reqCounter.metric.value.get() shouldBe 1
            eventually { // the response metric is invoked *after* the body is sent, so we might have to wait
              resCounter.metric.value.get() shouldBe 1
            }
          } >> basicRequest // onDecodeFailure path
          .post(uri"$baseUri/api/echo")
          .body("""{"invalid":"body",}""")
          .send(backend)
          .map { _ =>
            reqCounter.metric.value.get() shouldBe 2
            eventually {
              resCounter.metric.value.get() shouldBe 2
            }
          }
      }
    }, {
      val resCounter = newResponseCounter[F]
      val metrics = new MetricsRequestInterceptor[F](List(resCounter), Seq.empty)

      testServer(
        in_input_stream_out_input_stream.name("metrics"),
        interceptors = (ci: CustomiseInterceptors[F, OPTIONS]) => ci.metricsInterceptor(metrics)
      )(is => blockingResult((new ByteArrayInputStream(inputStreamToByteArray(is)): InputStream).asRight[Unit])) { (backend, baseUri) =>
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
      val metrics = new MetricsRequestInterceptor[F](List(resCounter), Seq.empty)

      testServer(in_root_path.name("metrics"), interceptors = (ci: CustomiseInterceptors[F, OPTIONS]) => ci.metricsInterceptor(metrics))(
        _ => ().asRight[Unit].unit
      ) { (backend, baseUri) =>
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
      val metrics = new MetricsRequestInterceptor[F](List(reqCounter, resCounter), Seq.empty)

      testServer(
        in_root_path.name("metrics on exception"),
        interceptors = (ci: CustomiseInterceptors[F, OPTIONS]) => ci.metricsInterceptor(metrics)
      ) { _ =>
        Thread.sleep(100)
        throw new RuntimeException("Ups")
      } { (backend, baseUri) =>
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
    Metric[F, Counter](Counter(), onRequest = { (_, c, m) => m.unit(EndpointMetric().onEndpointRequest { _ => m.eval(c.++()) }) })

  def newResponseCounter[F[_]]: Metric[F, Counter] =
    Metric[F, Counter](Counter(), onRequest = { (_, c, m) => m.unit(EndpointMetric().onResponseBody { (_, _) => m.eval(c.++()) }) })
}
