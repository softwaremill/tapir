package sttp.tapir.metrics.opentelemetry

import io.opentelemetry.api.common.{AttributeKey, Attributes}
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.data.LongPointData
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Second, Span}
import sttp.tapir.TestUtil._
import sttp.tapir.internal.NoStreams
import sttp.tapir.metrics.MetricLabels
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureInterceptor, DefaultDecodeFailureHandler}
import sttp.tapir.server.interceptor.exception.{DefaultExceptionHandler, ExceptionInterceptor}
import sttp.tapir.server.interpreter.ServerInterpreter

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class OpenTelemetryMetricsTest extends AnyFlatSpec with Matchers {

  "default metrics" should "collect requests total" in {
    // given
    val provider = SdkMeterProvider.builder().build()
    val meter = provider.get("tapir-instrumentation")
    val serverEp = PersonsApi().serverEp
    val metrics = OpenTelemetryMetrics[Id](meter).withRequestsTotal()
    val interpreter =
      new ServerInterpreter[Any, Id, String, NoStreams](TestRequestBody, StringToResponseBody, List(metrics.metricsInterceptor()), _ => ())

    // when
    interpreter.apply(PersonsApi.request("Jacob"), serverEp)
    interpreter.apply(PersonsApi.request("Mike"), serverEp)
    interpreter.apply(PersonsApi.request("Janusz"), serverEp)

    // then
    val point = longSumData(provider).head
    point.getAttributes shouldBe Attributes.of(AttributeKey.stringKey("method"), "GET", AttributeKey.stringKey("path"), "/person")
    point.getValue shouldBe 3
  }

  "default metrics" should "collect requests active" in {
    // given
    val provider = SdkMeterProvider.builder().build()
    val meter = provider.get("tapir-instrumentation")
    val serverEp = PersonsApi { name =>
      Thread.sleep(900)
      PersonsApi.defaultLogic(name)
    }.serverEp
    val metrics = OpenTelemetryMetrics[Id](meter).withRequestsActive()
    val interpreter =
      new ServerInterpreter[Any, Id, String, NoStreams](TestRequestBody, StringToResponseBody, List(metrics.metricsInterceptor()), _ => ())

    // when
    val response = Future { interpreter.apply(PersonsApi.request("Jacob"), serverEp) }

    Thread.sleep(100)

    // then
    val point = longSumData(provider).head
    point.getAttributes shouldBe Attributes.of(AttributeKey.stringKey("method"), "GET", AttributeKey.stringKey("path"), "/person")
    point.getValue shouldBe 1

    ScalaFutures.whenReady(response, Timeout(Span(1, Second))) { _ =>
      val point = longSumData(provider).head
      point.getAttributes shouldBe Attributes.of(AttributeKey.stringKey("method"), "GET", AttributeKey.stringKey("path"), "/person")
      point.getValue shouldBe 0
    }
  }

  "default metrics" should "collect responses total" in {
    // given
    val provider = SdkMeterProvider.builder().build()
    val meter = provider.get("tapir-instrumentation")
    val serverEp = PersonsApi().serverEp
    val metrics = OpenTelemetryMetrics[Id](meter).withResponsesTotal()
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
    longSumData(provider)
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

  "default metrics" should "collect responses duration" in {
    // given
    val provider = SdkMeterProvider.builder().build()
    val meter = provider.get("tapir-instrumentation")
    val waitServerEp: Int => ServerEndpoint[Any, Id] = millis => {
      PersonsApi { name =>
        Thread.sleep(millis)
        PersonsApi.defaultLogic(name)
      }.serverEp
    }

    val metrics = OpenTelemetryMetrics[Id](meter).withResponsesDuration()
    val interpreter =
      new ServerInterpreter[Any, Id, String, NoStreams](TestRequestBody, StringToResponseBody, List(metrics.metricsInterceptor()), _ => ())

    // when
    interpreter.apply(PersonsApi.request("Jacob"), waitServerEp(100))
    interpreter.apply(PersonsApi.request("Jacob"), waitServerEp(200))
    interpreter.apply(PersonsApi.request("Jacob"), waitServerEp(300))

    val point = provider.collectAllMetrics().asScala.head.getDoubleSummaryData.getPoints.asScala.head
    point.getAttributes shouldBe Attributes.of(
      AttributeKey.stringKey("method"),
      "GET",
      AttributeKey.stringKey("path"),
      "/person",
      AttributeKey.stringKey("status"),
      "2xx"
    )
    point.getPercentileValues.size() shouldBe 2
  }

  "default metrics" should "customize labels" in {
    // given
    val serverEp = PersonsApi().serverEp
    val labels = MetricLabels(forRequest = Seq("key" -> { case (_, _) => "value" }), forResponse = Seq())
    val provider = SdkMeterProvider.builder().build()
    val meter = provider.get("tapir-instrumentation")
    val metrics = OpenTelemetryMetrics[Id](meter).withResponsesTotal(labels)
    val interpreter =
      new ServerInterpreter[Any, Id, String, NoStreams](TestRequestBody, StringToResponseBody, List(metrics.metricsInterceptor()), _ => ())

    // when
    interpreter.apply(PersonsApi.request("Jacob"), serverEp)

    // then
    val point = longSumData(provider).head
    point.getAttributes shouldBe Attributes.of(AttributeKey.stringKey("key"), "value")
  }

  "metrics" should "be collected on exception when response from exception handler" in {
    val serverEp = PersonsApi { _ => throw new RuntimeException("Ups") }.serverEp
    val provider = SdkMeterProvider.builder().build()
    val meter = provider.get("tapir-instrumentation")
    val metrics = OpenTelemetryMetrics[Id](meter).withResponsesTotal()
    val interpreter = new ServerInterpreter[Any, Id, String, NoStreams](
      TestRequestBody,
      StringToResponseBody,
      List(metrics.metricsInterceptor(), new ExceptionInterceptor(DefaultExceptionHandler.handler)),
      _ => ()
    )

    // when
    interpreter.apply(PersonsApi.request("Jacob"), serverEp)

    // then
    val point = longSumData(provider).head
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

  private def longSumData(provider: SdkMeterProvider): List[LongPointData] =
    provider.collectAllMetrics().asScala.head.getLongSumData.getPoints.asScala.toList
}
