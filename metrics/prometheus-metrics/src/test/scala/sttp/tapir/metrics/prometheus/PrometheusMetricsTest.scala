package sttp.tapir.metrics.prometheus

import io.prometheus.client.CollectorRegistry
import org.scalatest.Assertions
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Second, Span}
import sttp.model.Uri._
import sttp.model._
import sttp.monad.syntax._
import sttp.tapir.TestUtil._
import sttp.tapir._
import sttp.tapir.metrics.prometheus.PrometheusMetrics._
import sttp.tapir.metrics.prometheus.PrometheusMetricsTest._
import sttp.tapir.model.{ConnectionInfo, ServerRequest}
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureInterceptor, DefaultDecodeFailureHandler}
import sttp.tapir.server.interceptor.exception.{DefaultExceptionHandler, ExceptionInterceptor}
import sttp.tapir.server.interpreter.ServerInterpreter

import scala.collection.immutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PrometheusMetricsTest extends AnyFlatSpec with Matchers {

  "default metrics" should "collect requests total" in {
    // given
    val serverEp: ServerEndpoint[String, String, String, Any, Id] = testEndpoint.serverLogic { name => testEndpointLogic(name) }
    val metrics = PrometheusMetrics[Id]("tapir", new CollectorRegistry()).withRequestsTotal()
    val interpreter =
      new ServerInterpreter[Any, Id, String, Nothing](TestRequestBody, StringToResponseBody, List(metrics.metricsInterceptor()), _ => ())

    // when
    interpreter.apply(testRequest("Jacob"), serverEp)
    interpreter.apply(testRequest("Mike"), serverEp)
    interpreter.apply(testRequest("Janusz"), serverEp)

    //then
    collectorRegistryCodec
      .encode(metrics.registry)
      .contains("tapir_requests_total{path=\"/person\",method=\"GET\",} 3.0") shouldBe true
  }

  "default metrics" should "collect requests active" in {
    // given
    val serverEp: ServerEndpoint[String, String, String, Any, Id] = testEndpoint.serverLogic { name =>
      Thread.sleep(900)
      testEndpointLogic(name)
    }
    val metrics = PrometheusMetrics[Id]("tapir", new CollectorRegistry()).withRequestsActive()
    val interpreter =
      new ServerInterpreter[Any, Id, String, Nothing](TestRequestBody, StringToResponseBody, List(metrics.metricsInterceptor()), _ => ())

    // when
    val response = Future { interpreter.apply(testRequest("Jacob"), serverEp) }

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
    val serverEp: ServerEndpoint[String, String, String, Any, Id] = testEndpoint.serverLogic { name => testEndpointLogic(name) }
    val metrics = PrometheusMetrics[Id]("tapir", new CollectorRegistry()).withResponsesTotal()
    val interpreter = new ServerInterpreter[Any, Id, Unit, Nothing](
      TestRequestBody,
      UnitToResponseBody,
      List(metrics.metricsInterceptor(), new DecodeFailureInterceptor(DefaultDecodeFailureHandler.handler)),
      _ => ()
    )

    // when
    interpreter.apply(testRequest("Jacob"), serverEp)
    interpreter.apply(testRequest("Jacob"), serverEp)
    interpreter.apply(testRequest("Mike"), serverEp)
    interpreter.apply(testRequest(""), serverEp)

    // then
    val encoded = collectorRegistryCodec.encode(metrics.registry)
    encoded.contains("tapir_responses_total{path=\"/person\",method=\"GET\",status=\"2xx\",} 2.0") shouldBe true
    encoded.contains("tapir_responses_total{path=\"/person\",method=\"GET\",status=\"4xx\",} 2.0") shouldBe true
  }

  "default metrics" should "collect responses duration" in {
    // given
    val waitServerEp: Int => ServerEndpoint[String, String, String, Any, Id] = millis => {
      testEndpoint.serverLogic { name =>
        Thread.sleep(millis)
        testEndpointLogic(name)
      }
    }

    val metrics = PrometheusMetrics[Id]("tapir", new CollectorRegistry()).withResponsesDuration()
    val interpreter =
      new ServerInterpreter[Any, Id, String, Nothing](TestRequestBody, StringToResponseBody, List(metrics.metricsInterceptor()), _ => ())

    // when
    interpreter.apply(testRequest("Jacob"), waitServerEp(100))
    interpreter.apply(testRequest("Jacob"), waitServerEp(200))
    interpreter.apply(testRequest("Jacob"), waitServerEp(300))

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
    val serverEp: ServerEndpoint[String, String, String, Any, Id] = testEndpoint.serverLogic { name => testEndpointLogic(name) }
    val labels = PrometheusLabels(forRequest = Seq("key" -> { case (_, _) => "value" }), forResponse = Seq())

    val metrics = PrometheusMetrics[Id]("tapir", new CollectorRegistry()).withResponsesTotal(labels)
    val interpreter =
      new ServerInterpreter[Any, Id, String, Nothing](TestRequestBody, StringToResponseBody, List(metrics.metricsInterceptor()), _ => ())

    // when
    interpreter.apply(testRequest("Jacob"), serverEp)

    // then
    collectorRegistryCodec.encode(metrics.registry).contains("tapir_responses_total{key=\"value\",} 1.0") shouldBe true
  }

  "interceptor" should "not collect metrics from prometheus endpoint" in {
    // given
    val serverEp: ServerEndpoint[String, String, String, Any, Id] = testEndpoint.serverLogic { name => testEndpointLogic(name) }
    val metrics = PrometheusMetrics[Id]("tapir", new CollectorRegistry()).withResponsesTotal()
    val interpreter =
      new ServerInterpreter[Any, Id, String, Nothing](TestRequestBody, StringToResponseBody, List(metrics.metricsInterceptor()), _ => ())
    val ses = List(
      metrics.metricsEndpoint.serverLogic { _ => idMonadError.unit(Right(metrics.registry).asInstanceOf[Either[Unit, CollectorRegistry]]) },
      serverEp
    )

    // when
    interpreter.apply(getMetricsRequest, ses)
    interpreter.apply(getMetricsRequest, ses)

    // then
    collectorRegistryCodec.encode(metrics.registry) shouldBe
      """# HELP tapir_responses_total HTTP responses
        |# TYPE tapir_responses_total counter
        |""".stripMargin
  }

  "metrics server endpoint" should "return encoded registry" in {
    // given
    val metrics = PrometheusMetrics[Id]("tapir", new CollectorRegistry()).withResponsesTotal()
    val interpreter =
      new ServerInterpreter[Any, Id, String, Nothing](TestRequestBody, StringToResponseBody, List(metrics.metricsInterceptor()), _ => ())

    // when
    for {
      response <- interpreter.apply(
        getMetricsRequest,
        metrics.metricsEndpoint.serverLogic { _ =>
          idMonadError.unit(Right(metrics.registry).asInstanceOf[Either[Unit, CollectorRegistry]])
        }
      )
    } yield {
      // then
      response.body.map { b =>
        b shouldBe """# HELP tapir_responses_total HTTP responses
                     |# TYPE tapir_responses_total counter
                     |""".stripMargin
      } getOrElse Assertions.fail()
    }
  }

  "metrics" should "be collected on exception when response from exception handler" in {
    // given
    val serverEp: ServerEndpoint[String, String, String, Any, Id] = testEndpoint.serverLogic { _ => throw new RuntimeException("Ups") }
    val metrics = PrometheusMetrics[Id]("tapir", new CollectorRegistry()).withResponsesTotal()
    val interpreter = new ServerInterpreter[Any, Id, String, Nothing](
      TestRequestBody,
      StringToResponseBody,
      List(metrics.metricsInterceptor(), new ExceptionInterceptor(DefaultExceptionHandler)),
      _ => ()
    )

    // when
    interpreter.apply(testRequest("Jacob"), serverEp)

    // then
    val r = collectorRegistryCodec.encode(metrics.registry)
    println(r)
  }

}

object PrometheusMetricsTest {

  import sttp.tapir.TestUtil._

  val testEndpoint: Endpoint[String, String, String, Any] = endpoint
    .in("person")
    .in(query[String]("name"))
    .out(stringBody)
    .errorOut(stringBody)

  val testEndpointLogic: String => Id[Either[String, String]] = name => (if (name == "Jacob") Right("hello") else Left(":(")).unit

  val testRequest: String => ServerRequest = name => {
    new ServerRequest {
      override def protocol: String = ""
      override def connectionInfo: ConnectionInfo = ConnectionInfo(None, None, None)
      override def underlying: Any = ()
      override def pathSegments: List[String] = List("person")
      override def queryParameters: QueryParams = if (name == "") QueryParams.apply() else QueryParams.fromSeq(Seq(("name", name)))
      override def method: Method = Method.GET
      override def uri: Uri = uri"http://example.com/person"
      override def headers: immutable.Seq[Header] = Nil
    }
  }

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
