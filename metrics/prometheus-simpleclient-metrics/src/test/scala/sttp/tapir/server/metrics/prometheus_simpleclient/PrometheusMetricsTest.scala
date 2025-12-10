package sttp.tapir.server.metrics.prometheus_simpleclient

import io.prometheus.client.CollectorRegistry
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Seconds, Span}
import sttp.model.Uri._
import sttp.model._
import sttp.tapir.TestUtil._
import sttp.tapir.server.TestUtil._
import PrometheusMetrics._
import PrometheusMetricsTest._
import sttp.shared.Identity
import sttp.tapir.AttributeKey
import sttp.tapir.capabilities.NoStreams
import sttp.tapir.model.{ConnectionInfo, ServerRequest}
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.RequestResult
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureInterceptor, DefaultDecodeFailureHandler}
import sttp.tapir.server.interceptor.exception.{DefaultExceptionHandler, ExceptionInterceptor}
import sttp.tapir.server.interpreter.{BodyListener, ServerInterpreter}
import sttp.tapir.server.metrics.MetricLabels

import java.time.{Clock, Instant, ZoneId}
import scala.collection.immutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Success, Try}
import sttp.tapir.server.interceptor.reject.RejectInterceptor
import sttp.tapir.server.interceptor.reject.DefaultRejectHandler

class PrometheusMetricsTest extends AnyFlatSpec with Matchers {

  "default metrics" should "collect requests active" in {
    // given
    val serverEp = PersonsApi { name =>
      Thread.sleep(2000)
      PersonsApi.defaultLogic(name)
    }.serverEp
    val metrics = PrometheusMetrics[Identity]("tapir", new CollectorRegistry()).addRequestsActive()
    val interpreter =
      new ServerInterpreter[Any, Identity, String, NoStreams](
        _ => List(serverEp),
        TestRequestBody,
        StringToResponseBody,
        List(metrics.metricsInterceptor()),
        _ => ()
      )

    // when
    val response = Future { interpreter.apply(PersonsApi.request("Jacob")) }

    Thread.sleep(500)

    // then
    collectorRegistryCodec
      .encode(metrics.registry) should include regex "tapir_request_active\\{(?=.*method=\"GET\").*\\} 1.0"

    ScalaFutures.whenReady(response, Timeout(Span(3, Seconds))) { _ =>
      collectorRegistryCodec
        .encode(metrics.registry) should include regex "tapir_request_active\\{(?=.*method=\"GET\").*\\} 0.0"
    }
  }

  "default metrics" should "collect requests total" in {
    // given
    val serverEp = PersonsApi().serverEp
    val metrics = PrometheusMetrics[Identity]("tapir", new CollectorRegistry()).addRequestsTotal()
    val interpreter = new ServerInterpreter[Any, Identity, Unit, NoStreams](
      _ => List(serverEp),
      TestRequestBody,
      UnitToResponseBody,
      List(metrics.metricsInterceptor(), new DecodeFailureInterceptor(DefaultDecodeFailureHandler[Identity])),
      _ => ()
    )

    // when
    interpreter.apply(PersonsApi.request("Jacob"))
    interpreter.apply(PersonsApi.request("Jacob"))
    interpreter.apply(PersonsApi.request("Mike"))
    interpreter.apply(PersonsApi.request(""))

    // then
    val encoded = collectorRegistryCodec.encode(metrics.registry)
    encoded should include regex "tapir_request_total\\{(?=.*path=\"/person\")(?=.*method=\"GET\")(?=.*status=\"2xx\").*\\} 2.0"
    encoded should include regex "tapir_request_total\\{(?=.*path=\"/person\")(?=.*method=\"GET\")(?=.*status=\"4xx\").*\\} 2.0"
  }

  "default metrics" should "collect requests duration" in {
    // given
    val clock = new TestClock()
    val waitServerEp: Long => ServerEndpoint[Any, Identity] = millis => {
      PersonsApi { name =>
        clock.forward(millis)
        PersonsApi.defaultLogic(name)
      }.serverEp
    }
    val waitBodyListener: Long => BodyListener[Identity, String] = millis =>
      new BodyListener[Identity, String] {
        override def onComplete(body: String)(cb: Try[Unit] => Identity[Unit]): String = {
          clock.forward(millis)
          cb(Success(()))
          body
        }
      }

    val metrics = PrometheusMetrics[Identity]("tapir", new CollectorRegistry()).addRequestsDuration(clock = clock)
    def interpret(sleepHeaders: Long, sleepBody: Long) =
      new ServerInterpreter[Any, Identity, String, NoStreams](
        _ => List(waitServerEp(sleepHeaders)),
        TestRequestBody,
        StringToResponseBody,
        List(metrics.metricsInterceptor()),
        _ => ()
      )(implicitly, waitBodyListener(sleepBody)).apply(PersonsApi.request("Jacob"))

    // when
    interpret(101, 1001)
    interpret(201, 2001)
    interpret(301, 3001)

    // then
    val encoded = collectorRegistryCodec.encode(metrics.registry)

    // headers
    // no response in less than 100ms
    // \{(?=.*path="/person")(?=.*method="GET")(?=.*status="2xx")(?=.*phase="headers")(?=.*le="0.25").*\}
    encoded should include regex "tapir_request_duration_seconds_bucket\\{(?=.*path=\"/person\")(?=.*method=\"GET\")(?=.*status=\"2xx\")(?=.*phase=\"headers\")(?=.*le=\"0.1\").*\\} 0"

    // two under 250ms
    encoded should include regex "tapir_request_duration_seconds_bucket\\{(?=.*path=\"/person\")(?=.*method=\"GET\")(?=.*status=\"2xx\")(?=.*phase=\"headers\")(?=.*le=\"0.25\").*\\} 2"

    // all under 500ms
    encoded should include regex "tapir_request_duration_seconds_bucket\\{(?=.*path=\"/person\")(?=.*method=\"GET\")(?=.*status=\"2xx\")(?=.*phase=\"headers\")(?=.*le=\"0.5\").*\\} 3"

    // body
    // no response in less than 1000ms
    encoded should include regex "tapir_request_duration_seconds_bucket\\{(?=.*path=\"/person\")(?=.*method=\"GET\")(?=.*status=\"2xx\")(?=.*phase=\"body\")(?=.*le=\"1.0\").*\\} 0"

    // two under 2500ms
    encoded should include regex "tapir_request_duration_seconds_bucket\\{(?=.*path=\"/person\")(?=.*method=\"GET\")(?=.*status=\"2xx\")(?=.*phase=\"body\")(?=.*le=\"2.5\").*\\} 2"

    // all under 5000ms
    encoded should include regex "tapir_request_duration_seconds_bucket\\{(?=.*path=\"/person\")(?=.*method=\"GET\")(?=.*status=\"2xx\")(?=.*phase=\"body\")(?=.*le=\"5.0\").*\\} 3"
  }

  "default metrics" should "customize labels" in {
    // given
    val serverEp = PersonsApi().serverEp
    val labels = MetricLabels(forRequest = List("key" -> { case _ => "value" }), forResponse = Nil, forEndpoint = Nil)

    val metrics = PrometheusMetrics[Identity]("tapir", new CollectorRegistry()).addRequestsTotal(labels)
    val interpreter =
      new ServerInterpreter[Any, Identity, String, NoStreams](
        _ => List(serverEp),
        TestRequestBody,
        StringToResponseBody,
        List(metrics.metricsInterceptor()),
        _ => ()
      )

    // when
    interpreter.apply(PersonsApi.request("Jacob"))

    // then
    collectorRegistryCodec.encode(metrics.registry) should include regex "tapir_request_total\\{(?=.*key=\"value\").*\\} 1.0"
  }

  "interceptor" should "not collect metrics from prometheus endpoint" in {
    // given
    val serverEp = PersonsApi().serverEp
    val metrics = PrometheusMetrics[Identity]("tapir", new CollectorRegistry()).addRequestsTotal()
    val interpreter =
      new ServerInterpreter[Any, Identity, String, NoStreams](
        _ => List(metrics.metricsEndpoint, serverEp),
        TestRequestBody,
        StringToResponseBody,
        List(metrics.metricsInterceptor()),
        _ => ()
      )

    // when
    interpreter.apply(getMetricsRequest)
    interpreter.apply(getMetricsRequest)

    // then
    collectorRegistryCodec.encode(metrics.registry) shouldBe
      """# HELP tapir_request_total Total HTTP requests
        |# TYPE tapir_request_total counter
        |""".stripMargin
  }

  "metrics server endpoint" should "return encoded registry" in {
    // given
    val metrics = PrometheusMetrics[Identity]("tapir", new CollectorRegistry()).addRequestsTotal()
    val interpreter =
      new ServerInterpreter[Any, Identity, String, NoStreams](
        _ => List(metrics.metricsEndpoint),
        TestRequestBody,
        StringToResponseBody,
        List(metrics.metricsInterceptor()),
        _ => ()
      )

    // when
    interpreter.apply(getMetricsRequest) match {
      case RequestResult.Response(response, _) =>
        response.body.map { b =>
          b shouldBe """# HELP tapir_request_total Total HTTP requests
                       |# TYPE tapir_request_total counter
                       |""".stripMargin
        } getOrElse fail()
      case _ => fail()
    }
  }

  "metrics" should "be collected on exception when response from exception handler" in {
    // given
    val serverEp = PersonsApi { _ => throw new RuntimeException("Ups") }.serverEp
    val metrics = PrometheusMetrics[Identity]("tapir", new CollectorRegistry()).addRequestsTotal()
    val interpreter = new ServerInterpreter[Any, Identity, String, NoStreams](
      _ => List(serverEp),
      TestRequestBody,
      StringToResponseBody,
      List(metrics.metricsInterceptor(), new ExceptionInterceptor(DefaultExceptionHandler[Identity])),
      _ => ()
    )

    // when
    interpreter.apply(PersonsApi.request("Jacob"))

    // then
    collectorRegistryCodec.encode(
      metrics.registry
    ) should include regex "tapir_request_total\\{(?=.*path=\"/person\")(?=.*method=\"GET\")(?=.*status=\"5xx\").*\\} 1.0"
  }

  "metrics" should "record a method not allowed response" in {
    // given
    val serverEp = PersonsApi().serverEp
    val metrics = PrometheusMetrics[Identity]("tapir", new CollectorRegistry()).addRequestsTotal()
    val interpreter = new ServerInterpreter[Any, Identity, String, NoStreams](
      _ => List(serverEp),
      TestRequestBody,
      StringToResponseBody,
      List(metrics.metricsInterceptor(), new RejectInterceptor(DefaultRejectHandler[Identity])),
      _ => ()
    )

    // when
    interpreter.apply(serverRequestFromUri(uri"http://example.com/person?name=Adam", _method = Method.POST))

    // then
    collectorRegistryCodec.encode(
      metrics.registry
    ) should include regex "tapir_request_total\\{(?=.*path=\"/__interceptor__\")(?=.*method=\"POST\")(?=.*status=\"4xx\").*\\} 1.0"
  }

  "metrics" should "record request duration for interceptor response" in {
    // given
    val serverEp = PersonsApi().serverEp
    val clock = new TestClock()
    val metrics = PrometheusMetrics[Identity]("tapir", new CollectorRegistry()).addRequestsDuration(clock = clock)
    val interpreter = new ServerInterpreter[Any, Identity, String, NoStreams](
      _ => List(serverEp),
      TestRequestBody,
      StringToResponseBody,
      List(metrics.metricsInterceptor(), new RejectInterceptor(DefaultRejectHandler[Identity])),
      _ => ()
    )

    // when
    clock.forward(150)
    interpreter.apply(serverRequestFromUri(uri"http://example.com/person?name=Adam", _method = Method.POST))

    // then
    val encoded = collectorRegistryCodec.encode(metrics.registry)
    encoded should include regex "tapir_request_duration_seconds_bucket\\{(?=.*path=\"/__interceptor__\")(?=.*method=\"POST\")(?=.*status=\"4xx\")(?=.*phase=\"body\")(?=.*le=\"0.25\").*\\} 1.0"
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
    override def attribute[T](k: AttributeKey[T]): Option[T] = None
    override def attribute[T](k: AttributeKey[T], v: T): ServerRequest = this
    override def withUnderlying(underlying: Any): ServerRequest = this
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
