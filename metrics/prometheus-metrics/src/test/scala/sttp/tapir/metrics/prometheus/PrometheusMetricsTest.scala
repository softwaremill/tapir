package sttp.tapir.metrics.prometheus

import io.prometheus.client.CollectorRegistry
import org.scalatest.Assertions
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Second, Span}
import sttp.capabilities
import sttp.model.Uri._
import sttp.model._
import sttp.tapir.TestUtil._
import sttp.tapir._
import sttp.tapir.internal.NoStreams
import sttp.tapir.metrics.Metric
import sttp.tapir.metrics.prometheus.PrometheusMetrics._
import sttp.tapir.metrics.prometheus.PrometheusMetricsTest._
import sttp.tapir.model.{ConnectionInfo, ServerRequest}
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureInterceptor, DefaultDecodeFailureHandler}
import sttp.tapir.server.interceptor.exception.{DefaultExceptionHandler, ExceptionInterceptor}
import sttp.tapir.server.interpreter.{BodyListener, ServerInterpreter, ToResponseBody}

import java.nio.charset.Charset
import scala.collection.immutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PrometheusMetricsTest extends AnyFlatSpec with Matchers {

  "default metrics" should "collect requests total" in {
    // given
    val serverEp: ServerEndpoint[String, Unit, Unit, Any, Id] = testEndpoint.serverLogic { name =>
      if (name == "Jacob") Right(()) else Left(())
    }
    val metrics = PrometheusMetrics("tapir", new CollectorRegistry()).withRequestsTotal()
    val interpreter = new ServerInterpreter[Any, Id, Unit, Nothing](TestRequestBody, TestToResponseBody, List(metrics.metricsInterceptor()))

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
    val serverEp: ServerEndpoint[String, Unit, Unit, Any, Id] = testEndpoint.serverLogic { _ =>
//      Thread.sleep(100)
      idMonadError.unit(Right(Thread.sleep(100)))
    }

    val metrics = PrometheusMetrics("tapir", new CollectorRegistry()).withRequestsActive()
    val interpreter = new ServerInterpreter[Any, Id, Unit, Nothing](TestRequestBody, TestToResponseBody, List(metrics.metricsInterceptor()(listenerUnit)))

    // when
    val request = Future { interpreter.apply(testRequest("Jacob"), serverEp) }

    Thread.sleep(50)

    // then
    collectorRegistryCodec
      .encode(metrics.registry)
      .contains("tapir_requests_active{path=\"/person\",method=\"GET\",} 1.0") shouldBe true

    ScalaFutures.whenReady(request, Timeout(Span(1, Second))) { _ =>
      collectorRegistryCodec
        .encode(metrics.registry)
        .contains("tapir_requests_active{path=\"/person\",method=\"GET\",} 0.0") shouldBe true
    }
  }

  "default metrics" should "collect responses total" in {
    // given
    val serverEp: ServerEndpoint[String, Unit, Unit, Any, Id] = testEndpoint.serverLogic { name =>
      if (name == "Jacob") Right(()) else Left(())
    }
    val metrics = PrometheusMetrics("tapir", new CollectorRegistry()).withResponsesTotal()
    val interpreter = new ServerInterpreter[Any, Id, Unit, Nothing](
      TestRequestBody,
      TestToResponseBody,
      List(metrics.metricsInterceptor(), new DecodeFailureInterceptor(DefaultDecodeFailureHandler.handler))
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
    val waitServerEp: Int => ServerEndpoint[String, Unit, Unit, Any, Id] = millis => {
      testEndpoint.serverLogic { _ =>
        Thread.sleep(millis)
        idMonadError.unit(Right(()))
      }
    }

    val metrics = PrometheusMetrics("tapir", new CollectorRegistry()).withResponsesDuration()
    val interpreter = new ServerInterpreter[Any, Id, Unit, Nothing](TestRequestBody, TestToResponseBody, List(metrics.metricsInterceptor()))

    // when
    interpreter.apply(testRequest("Mike"), waitServerEp(100))
    interpreter.apply(testRequest("Mike"), waitServerEp(200))
    interpreter.apply(testRequest("Mike"), waitServerEp(300))

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
    val serverEp: ServerEndpoint[String, Unit, Unit, Any, Id] = testEndpoint.serverLogic { name =>
      if (name == "Jacob") Right(()) else Left(())
    }

    val labels = PrometheusLabels(forRequest = Seq("key" -> { case (_, _) => "value" }), forResponse = Seq())

    val metrics = PrometheusMetrics("tapir", new CollectorRegistry()).withResponsesTotal(labels)
    val interpreter = new ServerInterpreter[Any, Id, Unit, Nothing](TestRequestBody, TestToResponseBody, List(metrics.metricsInterceptor()))

    // when
    interpreter.apply(testRequest("Jacob"), serverEp)

    // then
    collectorRegistryCodec.encode(metrics.registry).contains("tapir_responses_total{key=\"value\",} 1.0") shouldBe true
  }

  "interceptor" should "not collect metrics from prometheus endpoint" in {
    // given
    val serverEp: ServerEndpoint[String, Unit, Unit, Any, Id] = testEndpoint.serverLogic { _ => idMonadError.unit(Right(())) }
    val metrics = PrometheusMetrics("tapir", new CollectorRegistry()).withResponsesTotal()
    val interpreter = new ServerInterpreter[Any, Id, Unit, Nothing](TestRequestBody, TestToResponseBody, List(metrics.metricsInterceptor()))
    val ses = List(metrics.metricsServerEndpoint, serverEp)

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
    val toResponseBody: ToResponseBody[String, Nothing] = new ToResponseBody[String, Nothing] {
      override val streams: capabilities.Streams[Nothing] = NoStreams
      override def fromRawValue[R](v: R, headers: HasHeaders, format: CodecFormat, bodyType: RawBodyType[R]): String =
        v.asInstanceOf[String]
      override def fromStreamValue(v: streams.BinaryStream, headers: HasHeaders, format: CodecFormat, charset: Option[Charset]): String = ""
      override def fromWebSocketPipe[REQ, RESP](
          pipe: streams.Pipe[REQ, RESP],
          o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, Nothing]
      ): String = ""
    }

    val metrics = PrometheusMetrics("tapir", new CollectorRegistry()).withResponsesTotal()
    val interpreter =
      new ServerInterpreter[Any, Id, String, Nothing](TestRequestBody, toResponseBody, List(metrics.metricsInterceptor()))

    // when
    for {
      response <- interpreter.apply(getMetricsRequest, metrics.metricsServerEndpoint)
    } yield {
      // then
      response.body.map { b =>
        b shouldBe """# HELP tapir_responses_total HTTP responses
                     |# TYPE tapir_responses_total counter
                     |""".stripMargin
      } getOrElse Assertions.fail()
    }
  }

  "metrics" should "be unique" in {
    PrometheusMetrics("tapir", new CollectorRegistry())
      .withCustom(Metric[Id, Int](0))
      .withCustom(Metric[Id, Int](0))
      .metrics
      .size shouldBe 1
  }

  "metrics" should "be collected on exception" in {
    // given
    val serverEp: ServerEndpoint[String, Unit, Unit, Any, Id] = testEndpoint.serverLogic { _ => throw new RuntimeException("Ups") }
    val metrics = PrometheusMetrics("tapir", new CollectorRegistry()).withResponsesTotal()
    val interpreter = new ServerInterpreter[Any, Id, Unit, Nothing](
      TestRequestBody,
      TestToResponseBody,
      List(metrics.metricsInterceptor(), new ExceptionInterceptor(DefaultExceptionHandler))
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

  implicit val listenerUnit: BodyListener[Id, Unit] = new BodyListener[Id, Unit] {
    override def listen(body: Unit)(cb: => Id[Unit]): Unit = {
      cb
      ()
    }
  }

  implicit val listenerString: BodyListener[Id, String] = new BodyListener[Id, String] {
    override def listen(body: String)(cb: => Id[Unit]): String = {
      cb
      body
    }
  }

  val testEndpoint: Endpoint[String, Unit, Unit, Any] = endpoint
    .in("person")
    .in(query[String]("name"))
    .out(emptyOutput)
    .errorOut(emptyOutput)

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
