package sttp.tapir.server.metrics.opentelemetry

import io.opentelemetry.api.common.{AttributeKey, Attributes}
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.data.LongPointData
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Seconds, Span}
import sttp.tapir.TestUtil._
import sttp.tapir.capabilities.NoStreams
import sttp.tapir.server.TestUtil._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureInterceptor, DefaultDecodeFailureHandler}
import sttp.tapir.server.interceptor.exception.{DefaultExceptionHandler, ExceptionInterceptor}
import sttp.tapir.server.interpreter.ServerInterpreter
import sttp.tapir.server.metrics.MetricLabels

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class OpenTelemetryMetricsTest extends AnyFlatSpec with Matchers {

  "default metrics" should "collect requests active" in {
    // given
    val reader = InMemoryMetricReader.create()
    val provider = SdkMeterProvider.builder().registerMetricReader(reader).build()
    val meter = provider.get("tapir-instrumentation")
    val serverEp = PersonsApi { name =>
      Thread.sleep(2000)
      PersonsApi.defaultLogic(name)
    }.serverEp
    val metrics = OpenTelemetryMetrics[Id](meter).addRequestsActive()
    val interpreter =
      new ServerInterpreter[Any, Id, String, NoStreams](
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
    val point = longSumData(reader).head
    point.getAttributes shouldBe Attributes.of(AttributeKey.stringKey("method"), "GET", AttributeKey.stringKey("path"), "/person")
    point.getValue shouldBe 1

    ScalaFutures.whenReady(response, Timeout(Span(3, Seconds))) { _ =>
      val point = longSumData(reader).head
      point.getAttributes shouldBe Attributes.of(AttributeKey.stringKey("method"), "GET", AttributeKey.stringKey("path"), "/person")
      point.getValue shouldBe 0
    }
  }

  "default metrics" should "collect requests total" in {
    // given
    val reader = InMemoryMetricReader.create()
    val provider = SdkMeterProvider.builder().registerMetricReader(reader).build()
    val meter = provider.get("tapir-instrumentation")
    val serverEp = PersonsApi().serverEp
    val metrics = OpenTelemetryMetrics[Id](meter).addRequestsTotal()
    val interpreter = new ServerInterpreter[Any, Id, Unit, NoStreams](
      _ => List(serverEp),
      TestRequestBody,
      UnitToResponseBody,
      List(metrics.metricsInterceptor(), new DecodeFailureInterceptor(DefaultDecodeFailureHandler[Id])),
      _ => ()
    )

    // when
    interpreter.apply(PersonsApi.request("Jacob"))
    interpreter.apply(PersonsApi.request("Jacob"))
    interpreter.apply(PersonsApi.request("Mike"))
    interpreter.apply(PersonsApi.request(""))

    // then
    longSumData(reader)
      .count {
        case dp
            if dp.getAttributes == Attributes.of(
              AttributeKey.stringKey("method"),
              "GET",
              AttributeKey.stringKey("path"),
              "/person",
              AttributeKey.stringKey("status"),
              "2xx"
            ) && dp.getValue == 2 =>
          true
        case dp
            if dp.getAttributes == Attributes.of(
              AttributeKey.stringKey("method"),
              "GET",
              AttributeKey.stringKey("path"),
              "/person",
              AttributeKey.stringKey("status"),
              "4xx"
            ) && dp.getValue == 2 =>
          true
        case _ => false
      } shouldBe 2
  }

  "default metrics" should "collect requests duration" in {
    // given
    val reader = InMemoryMetricReader.create()
    val provider = SdkMeterProvider.builder().registerMetricReader(reader).build()
    val meter = provider.get("tapir-instrumentation")
    val waitServerEp: Int => ServerEndpoint[Any, Id] = millis => {
      PersonsApi { name =>
        Thread.sleep(millis)
        PersonsApi.defaultLogic(name)
      }.serverEp
    }

    val metrics = OpenTelemetryMetrics[Id](meter).addRequestsDuration()
    def interpret(sleep: Int) =
      new ServerInterpreter[Any, Id, String, NoStreams](
        _ => List(waitServerEp(sleep)),
        TestRequestBody,
        StringToResponseBody,
        List(metrics.metricsInterceptor()),
        _ => ()
      )
        .apply(PersonsApi.request("Jacob"))

    // when
    interpret(100)
    interpret(200)
    interpret(300)

    val point = reader.collectAllMetrics().asScala.head.getHistogramData.getPoints.asScala
    point.map(_.getAttributes) should contain(
      Attributes.of(
        AttributeKey.stringKey("method"),
        "GET",
        AttributeKey.stringKey("path"),
        "/person",
        AttributeKey.stringKey("status"),
        "2xx",
        AttributeKey.stringKey("phase"),
        "body"
      )
    )
  }

  "default metrics" should "customize labels" in {
    // given
    val serverEp = PersonsApi().serverEp
    val labels = MetricLabels(forRequest = List("key" -> { case (_, _) => "value" }), forResponse = Nil)
    val reader = InMemoryMetricReader.create()
    val provider = SdkMeterProvider.builder().registerMetricReader(reader).build()
    val meter = provider.get("tapir-instrumentation")
    val metrics = OpenTelemetryMetrics[Id](meter).addRequestsTotal(labels)
    val interpreter =
      new ServerInterpreter[Any, Id, String, NoStreams](
        _ => List(serverEp),
        TestRequestBody,
        StringToResponseBody,
        List(metrics.metricsInterceptor()),
        _ => ()
      )

    // when
    interpreter.apply(PersonsApi.request("Jacob"))

    // then
    val point = longSumData(reader).head
    point.getAttributes shouldBe Attributes.of(AttributeKey.stringKey("key"), "value")
  }

  "metrics" should "be collected on exception when response from exception handler" in {
    val serverEp = PersonsApi { _ => throw new RuntimeException("Ups") }.serverEp
    val reader = InMemoryMetricReader.create()
    val provider = SdkMeterProvider.builder().registerMetricReader(reader).build()
    val meter = provider.get("tapir-instrumentation")
    val metrics = OpenTelemetryMetrics[Id](meter).addRequestsTotal()
    val interpreter = new ServerInterpreter[Any, Id, String, NoStreams](
      _ => List(serverEp),
      TestRequestBody,
      StringToResponseBody,
      List(metrics.metricsInterceptor(), new ExceptionInterceptor(DefaultExceptionHandler[Id])),
      _ => ()
    )

    // when
    interpreter.apply(PersonsApi.request("Jacob"))

    // then
    val point = longSumData(reader).head
    point.getAttributes shouldBe Attributes.of(
      AttributeKey.stringKey("method"),
      "GET",
      AttributeKey.stringKey("path"),
      "/person",
      AttributeKey.stringKey("status"),
      "5xx"
    )
    point.getValue shouldBe 1
  }

  private def longSumData(reader: InMemoryMetricReader): List[LongPointData] =
    reader.collectAllMetrics().asScala.head.getLongSumData.getPoints.asScala.toList
}
