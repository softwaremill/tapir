package sttp.tapir.metrics.prometheus

import io.prometheus.client.CollectorRegistry
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Second, Span}
import sttp.model.Uri._
import sttp.model._
import sttp.tapir.TestUtil._
import sttp.tapir.internal.NoStreams
import sttp.tapir.metrics.MetricLabels
import sttp.tapir.metrics.prometheus.PrometheusMetrics._
import sttp.tapir.metrics.prometheus.PrometheusMetricsTest._
import sttp.tapir.model.{ConnectionInfo, ServerRequest}
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.RequestResult
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureInterceptor, DefaultDecodeFailureHandler}
import sttp.tapir.server.interceptor.exception.{DefaultExceptionHandler, ExceptionInterceptor}
import sttp.tapir.server.interpreter.ServerInterpreter

import java.time.{Clock, Instant, ZoneId}
import scala.collection.immutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PrometheusMetricsTest extends AnyFlatSpec with Matchers {

  "default metrics" should "collect requests total" in {
    // given
    val serverEp = PersonsApi().serverEp
    val metrics = PrometheusMetrics[Id]("tapir", new CollectorRegistry()).withRequestsTotal()
    val interpreter =
      new ServerInterpreter[Any, Id, String, NoStreams](TestRequestBody, StringToResponseBody, List(metrics.metricsInterceptor()), _ => ())

    // when
    interpreter.apply(PersonsApi.request("Jacob"), serverEp)
    interpreter.apply(PersonsApi.request("Mike"), serverEp)
    interpreter.apply(PersonsApi.request("Janusz"), serverEp)

    // then
    collectorRegistryCodec
      .encode(metrics.registry)
      .contains("tapir_requests_total{path=\"/person\",method=\"GET\",} 3.0") shouldBe true
  }

  "default metrics" should "collect requests active" in {
    // given
    val serverEp = PersonsApi { name =>
      Thread.sleep(900)
      PersonsApi.defaultLogic(name)
    }.serverEp
    val metrics = PrometheusMetrics[Id]("tapir", new CollectorRegistry()).withRequestsActive()
    val interpreter =
      new ServerInterpreter[Any, Id, String, NoStreams](TestRequestBody, StringToResponseBody, List(metrics.metricsInterceptor()), _ => ())

    // when
    val response = Future { interpreter.apply(PersonsApi.request("Jacob"), serverEp) }

    Thread.sleep(100)

    // then
    collectorRegistryCodec
      .encode(metrics.registry)
      .contains("tapir_requests_active{path=\"/person\",method=\"GET\",} 1.0") shouldBe true

    ScalaFutures.whenReady(response, Timeout(Span(1, Second))) { _ =>
      collectorRegistryCodec
        .encode(metrics.registry)
        .contains("tapir_requests_active{path=\"/person\",method=\"GET\",} 0.0") shouldBe true
    }
  }

  "default metrics" should "collect responses total" in {
    // given
    val serverEp = PersonsApi().serverEp
    val metrics = PrometheusMetrics[Id]("tapir", new CollectorRegistry()).withResponsesTotal()
    val interpreter = new ServerInterpreter[Any, Id, Unit, NoStreams](
      TestRequestBody,
      UnitToResponseBody,
      List(metrics.metricsInterceptor(), new DecodeFailureInterceptor(DefaultDecodeFailureHandler.handler)),
      _ => ()
    )

    // when
    interpreter.apply(PersonsApi.request("Jacob"), serverEp)
    interpreter.apply(PersonsApi.request("Jacob"), serverEp)
    interpreter.apply(PersonsApi.request("Mike"), serverEp)
    interpreter.apply(PersonsApi.request(""), serverEp)

    // then
    val encoded = collectorRegistryCodec.encode(metrics.registry)
    encoded.contains("tapir_responses_total{path=\"/person\",method=\"GET\",status=\"2xx\",} 2.0") shouldBe true
    encoded.contains("tapir_responses_total{path=\"/person\",method=\"GET\",status=\"4xx\",} 2.0") shouldBe true
  }

  "default metrics" should "collect responses duration" in {
    // given
    val clock = new TestClock()
    val waitServerEp: Long => ServerEndpoint[Any, Id] = millis => {
      PersonsApi { name =>
        clock.forward(millis)
        PersonsApi.defaultLogic(name)
      }.serverEp
    }

    val metrics = PrometheusMetrics[Id]("tapir", new CollectorRegistry()).withResponsesDuration(clock = clock)
    val interpreter =
      new ServerInterpreter[Any, Id, String, NoStreams](TestRequestBody, StringToResponseBody, List(metrics.metricsInterceptor()), _ => ())

    // when
    interpreter.apply(PersonsApi.request("Jacob"), waitServerEp(101))
    interpreter.apply(PersonsApi.request("Jacob"), waitServerEp(201))
    interpreter.apply(PersonsApi.request("Jacob"), waitServerEp(301))

    // then
    val encoded = collectorRegistryCodec.encode(metrics.registry)

    // no response in less than 100ms
    encoded.contains("tapir_responses_duration_bucket{path=\"/person\",method=\"GET\",status=\"2xx\",le=\"0.1\",} 0.0") shouldBe true

    // two under 250ms
    encoded.contains(
      "tapir_responses_duration_bucket{path=\"/person\",method=\"GET\",status=\"2xx\",le=\"0.25\",} 2.0"
    ) shouldBe true

    // all under 500ms
    encoded.contains("tapir_responses_duration_bucket{path=\"/person\",method=\"GET\",status=\"2xx\",le=\"0.5\",} 3.0") shouldBe true
  }

  "default metrics" should "customize labels" in {
    // given
    val serverEp = PersonsApi().serverEp
    val labels = MetricLabels(forRequest = Seq("key" -> { case (_, _) => "value" }), forResponse = Seq())

    val metrics = PrometheusMetrics[Id]("tapir", new CollectorRegistry()).withResponsesTotal(labels)
    val interpreter =
      new ServerInterpreter[Any, Id, String, NoStreams](TestRequestBody, StringToResponseBody, List(metrics.metricsInterceptor()), _ => ())

    // when
    interpreter.apply(PersonsApi.request("Jacob"), serverEp)

    // then
    collectorRegistryCodec.encode(metrics.registry).contains("tapir_responses_total{key=\"value\",} 1.0") shouldBe true
  }

  "interceptor" should "not collect metrics from prometheus endpoint" in {
    // given
    val serverEp = PersonsApi().serverEp
    val metrics = PrometheusMetrics[Id]("tapir", new CollectorRegistry()).withResponsesTotal()
    val interpreter =
      new ServerInterpreter[Any, Id, String, NoStreams](TestRequestBody, StringToResponseBody, List(metrics.metricsInterceptor()), _ => ())
    val ses = List(metrics.metricsEndpoint, serverEp)

    // when
    interpreter.apply(getMetricsRequest, ses)
    interpreter.apply(getMetricsRequest, ses)

    // then
    collectorRegistryCodec.encode(metrics.registry) shouldBe
      """# HELP tapir_responses_total Total HTTP responses
        |# TYPE tapir_responses_total counter
        |""".stripMargin
  }

  "metrics server endpoint" should "return encoded registry" in {
    // given
    val metrics = PrometheusMetrics[Id]("tapir", new CollectorRegistry()).withResponsesTotal()
    val interpreter =
      new ServerInterpreter[Any, Id, String, NoStreams](TestRequestBody, StringToResponseBody, List(metrics.metricsInterceptor()), _ => ())

    // when
    interpreter.apply(getMetricsRequest, metrics.metricsEndpoint) match {
      case RequestResult.Response(response) =>
        response.body.map { b =>
          b shouldBe """# HELP tapir_responses_total Total HTTP responses
                     |# TYPE tapir_responses_total counter
                     |""".stripMargin
        } getOrElse fail()
      case _ => fail()
    }
  }

  "metrics" should "be collected on exception when response from exception handler" in {
    // given
    val serverEp = PersonsApi { _ => throw new RuntimeException("Ups") }.serverEp
    val metrics = PrometheusMetrics[Id]("tapir", new CollectorRegistry()).withResponsesTotal()
    val interpreter = new ServerInterpreter[Any, Id, String, NoStreams](
      TestRequestBody,
      StringToResponseBody,
      List(metrics.metricsInterceptor(), new ExceptionInterceptor(DefaultExceptionHandler.handler)),
      _ => ()
    )

    // when
    interpreter.apply(PersonsApi.request("Jacob"), serverEp)

    // then
    collectorRegistryCodec
      .encode(metrics.registry)
      .contains("tapir_responses_total{path=\"/person\",method=\"GET\",status=\"5xx\",} 1.0") shouldBe true
  }
}

object PrometheusMetricsTest {
  val getMetricsRequest: ServerRequest = new ServerRequest {
    override def protocol: String = ""
    override def connectionInfo: ConnectionInfo = ConnectionInfo(None, None, None)
    override def underlying: Any = ()
    override def pathSegments: List[String] = List("metrics")
    override def queryParameters: QueryParams = QueryParams.apply()
    override def method: Method = Method.GET
    override def uri: Uri = uri"http://example.com/metrics"
    override def headers: immutable.Seq[Header] = Nil
  }
}

class TestClock(start: Long = System.currentTimeMillis()) extends Clock {
  private var _millis = start

  def forward(m: Long): Unit = {
    _millis += m
  }

  override def getZone: ZoneId = Clock.systemUTC().getZone
  override def withZone(zone: ZoneId): Clock = this
  override def instant(): Instant = Instant.ofEpochMilli(_millis)
}
