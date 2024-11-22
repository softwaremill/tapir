package sttp.tapir.server.opentelemetry

import io.opentelemetry.api.trace.{Span, SpanKind, StatusCode, Tracer}
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.testing.trace.InMemorySpanExporter
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir._
import sttp.model.Headers
import sttp.model.Header
import sttp.model.StatusCode._
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor

class OpenTelemetryTracingSyncTest extends AnyFlatSpec with Matchers {
  
  val spanExporter = InMemorySpanExporter.create()
  val tracerProvider = SdkTracerProvider.builder()
    .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
    .build()
  val tracer: Tracer = tracerProvider.get("test")
  val propagator = W3CTraceContextPropagator.getInstance()

  override def beforeEach(): Unit = {
    spanExporter.reset()
  }

  it should "propagate tracing context" in {
    val tracing = new OpenTelemetryTracingSync(tracer, propagator)
    
    val traceparent = "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-01"
    val request = ServerRequest(
      method = Method.GET,
      uri = uri"http://test.com/test",
      headers = List(Header("traceparent", traceparent))
    )
    
    tracing(endpoint.get.in("test"), _ => RequestResult.Success(()), _ => RequestResult.Response("OK"))(request)
    
    val spans = spanExporter.getFinishedSpanItems
    val span = spans.get(0)
    
    span.getParentSpanContext.getTraceId should be ("0af7651916cd43dd8448eb211c80319c")
  }

  it should "handle errors correctly" in {
    val tracing = new OpenTelemetryTracingSync(tracer, propagator)
    val exception = new RuntimeException("test error")
    
    val request = ServerRequest(
      method = Method.GET,
      uri = uri"http://test.com/test",
      headers = List.empty
    )
    
    tracing(
      endpoint.get.in("test"), 
      _ => RequestResult.Success(()), 
      _ => RequestResult.Failure(exception)
    )(request)
    
    val spans = spanExporter.getFinishedSpanItems
    val span = spans.get(0)
    
    span.getStatus.isError should be (true)
    span.getEvents.size should be (1)
  }

  it should "include configured headers as span attributes" in {
    val config = OpenTelemetryConfig(
      includeHeaders = Set("user-agent", "x-request-id")
    )
    val tracing = new OpenTelemetryTracingSync(tracer, propagator, config)
    
    val request = ServerRequest(
      method = Method.GET,
      uri = uri"http://test.com/test",
      headers = List(
        Header("user-agent", "test-agent"),
        Header("x-request-id", "123")
      )
    )
    
    tracing(endpoint.get.in("test"), _ => RequestResult.Success(()), _ => RequestResult.Response("OK"))(request)
    
    val spans = spanExporter.getFinishedSpanItems
    val span = spans.get(0)
    
    span.getAttributes.get("http.header.user-agent").getStringValue should be ("test-agent")
    span.getAttributes.get("http.header.x-request-id").getStringValue should be ("123")
  }

  it should "support custom span naming" in {
    val config = OpenTelemetryConfig(
      spanNaming = SpanNaming.Custom(e => s"CUSTOM-${e.showShort}")
    )
    val tracing = new OpenTelemetryTracingSync(tracer, propagator, config)
    
    val request = ServerRequest(
      method = Method.GET,
      uri = uri"http://test.com/test",
      headers = List.empty
    )
    
    tracing(endpoint.get.in("test"), _ => RequestResult.Success(()), _ => RequestResult.Response("OK"))(request)
    
    val spans = spanExporter.getFinishedSpanItems
    val span = spans.get(0)
    
    span.getName should startWith ("CUSTOM-")
  }
}