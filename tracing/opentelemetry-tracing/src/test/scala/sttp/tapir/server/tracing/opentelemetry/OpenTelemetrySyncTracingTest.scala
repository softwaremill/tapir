package sttp.tapir.server.tracing.opentelemetry

import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.`export`.SimpleSpanProcessor
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.semconv.{HttpAttributes, ServerAttributes, UrlAttributes}
import org.scalatest.compatible.Assertion
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.model.Uri._
import sttp.model.headers.Forwarded
import sttp.model.{Header, HeaderNames}
import sttp.shared.Identity
import sttp.tapir._
import sttp.tapir.TestUtil._
import sttp.tapir.capabilities.NoStreams
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.TestUtil._
import sttp.tapir.server.interpreter.ServerInterpreter

import collection.JavaConverters._

class OpenTelemetrySyncTracingTest extends AnyFlatSpec with Matchers {
  def testEndpointWithSpan(e: ServerEndpoint[Any, Identity], r: ServerRequest)(verify: SpanData => Assertion): Assertion = {
    // given
    val spanExporter = InMemorySpanExporter.create()
    val tracerProvider = SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(spanExporter)).build()
    val otel = OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build()

    val interpreter =
      new ServerInterpreter[Any, Identity, String, NoStreams](
        _ => List(e),
        TestRequestBody,
        StringToResponseBody,
        List(OpenTelemetrySyncTracing(otel)),
        _ => ()
      )

    // when
    val _ = interpreter.apply(r)

    // then
    val spans = spanExporter.getFinishedSpanItems().asScala.toList
    spans should have size (1)

    val List(span) = spans
    verify(span)
  }

  it should "report a simple trace" in {
    testEndpointWithSpan(
      endpoint
        .in("person")
        .in(query[String]("name"))
        .out(stringBody)
        .errorOut(stringBody)
        .handle(_ => Right("hello")),
      serverRequestFromUri(uri"http://example.com/person?name=Adam")
    ) { span =>
      span.getName() shouldBe "GET /person"
      span.getAttributes().get(HttpAttributes.HTTP_RESPONSE_STATUS_CODE) shouldBe 200L
      span.getAttributes().get(UrlAttributes.URL_PATH) shouldBe "/person"
    }
  }

  it should "report a 404 span when the endpoint is not found" in {
    testEndpointWithSpan(
      endpoint
        .in("person")
        .out(stringBody)
        .errorOut(stringBody)
        .handle(_ => Right("hello")),
      serverRequestFromUri(uri"http://example.com/other")
    ) { span =>
      span.getName() shouldBe "GET"
      span.getAttributes().get(HttpAttributes.HTTP_RESPONSE_STATUS_CODE) shouldBe 404L
      span.getAttributes().get(UrlAttributes.URL_PATH) shouldBe "/other"
    }
  }

  it should "use the rendered path template as the span name" in {
    testEndpointWithSpan(
      endpoint
        .in("person" / path[String]("name") / path[String]("surname") / "info")
        .out(stringBody)
        .errorOut(stringBody)
        .handle(_ => Right("ok")),
      serverRequestFromUri(uri"http://example.com/person/Adam/Smith/info")
    ) { span =>
      span.getName() shouldBe "GET /person/{name}/{surname}/info"
      span.getAttributes().get(HttpAttributes.HTTP_RESPONSE_STATUS_CODE) shouldBe 200L
      span.getAttributes().get(UrlAttributes.URL_PATH) shouldBe "/person/Adam/Smith/info"
    }
  }

  it should "use the host from the forwarded header" in {
    testEndpointWithSpan(
      endpoint
        .in("person")
        .in(query[String]("name"))
        .out(stringBody)
        .errorOut(stringBody)
        .handle(_ => Right("hello")),
      serverRequestFromUri(
        uri"http://example.com/person?name=Adam",
        _headers = List(Header(HeaderNames.Forwarded, Forwarded(None, None, Some("softwaremill.com"), None).toString))
      )
    ) { span =>
      span.getAttributes().get(ServerAttributes.SERVER_ADDRESS) shouldBe "softwaremill.com"
    }
  }
}
