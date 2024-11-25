package sttp.tapir.server.opentelemetry

import io.opentelemetry.api.trace.{Span, SpanKind, StatusCode, Tracer}
import io.opentelemetry.context.{Context, ContextKey}
import io.opentelemetry.api.baggage.Baggage
import io.opentelemetry.context.propagation.{TextMapGetter, TextMapPropagator}
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interceptor.{EndpointInterceptor, RequestResult, SecureEndpointInterceptor}
import scala.util.control.NonFatal
import scala.jdk.CollectionConverters._

/**
 * OpenTelemetry tracing implementation for synchronous/direct-style endpoints.
 * Designed to be compatible with virtual threads (Project Loom).
 *
 * @param tracer OpenTelemetry tracer instance
 * @param propagator Context propagator (defaults to W3C)
 * @param config Tracing configuration
 */
class OpenTelemetryTracingSync(
    tracer: Tracer,
    propagator: TextMapPropagator,
    config: OpenTelemetryConfig = OpenTelemetryConfig()
) extends SecureEndpointInterceptor[Identity] {

  private val SERVER_SPAN_KEY = ContextKey.named("tapir-server-span")

  private val textMapGetter = new TextMapGetter[ServerRequest] {
    override def keys(carrier: ServerRequest): java.lang.Iterable[String] =
      carrier.headers.map(_.name).asJava

    override def get(carrier: ServerRequest, key: String): String =
      carrier.header(key).orNull
  }

  private def executeInVirtualThread[A](f: => A): A = {
    Thread.ofVirtual()
      .name(s"tapir-ot-${Thread.currentThread().getName}")
      .start(() => f)
      .join()
  }

  def apply[A](
      endpoint: Endpoint[_, _, _, _, _],
      securityLogic: EndpointInterceptor[Identity],
      delegate: EndpointInterceptor[Identity]
  ): EndpointInterceptor[Identity] =
    new EndpointInterceptor[Identity] {
      def apply(request: ServerRequest): RequestResult[Identity] = {
        executeInVirtualThread {
          val parentContext = propagator.extract(Context.current(), request, textMapGetter)
        
          val spanBuilder = tracer
            .spanBuilder(getSpanName(endpoint))
            .setParent(parentContext)
            .setSpanKind(SpanKind.SERVER)

          addRequestAttributes(spanBuilder, request)
        
          val span = spanBuilder.startSpan()
          try {
            val scopedContext = parentContext.`with`(SERVER_SPAN_KEY, span)
            Context.makeContext(scopedContext)
          
            if (config.includeBaggage) {
              addBaggageToSpan(span, Baggage.current())
            }

            val result = try {
              delegate(request)
            } catch {
              case NonFatal(e) =>
                span.recordException(e)
                span.setStatus(StatusCode.ERROR)
                throw e
            }

            handleResult(result, span)
            result
          } finally {
            span.end()
          }
        }
      }
    }

  private def getSpanName(endpoint: Endpoint[_, _, _, _, _]): String = 
    config.spanNaming match {
      case SpanNaming.Default => endpoint.showShort
      case SpanNaming.Path => endpoint.showPath
      case SpanNaming.Custom(f) => f(endpoint)
    }

  private def addRequestAttributes(spanBuilder: SpanBuilder, request: ServerRequest): Unit = {
    spanBuilder
      .setAttribute("http.method", request.method.method)
      .setAttribute("http.url", request.uri.toString)
      
    config.includeHeaders.foreach { headerName =>
      request.header(headerName).foreach { value =>
        spanBuilder.setAttribute(s"http.header.$headerName", value)
      }
    }
  }

  private def addBaggageToSpan(span: Span, baggage: Baggage): Unit = {
    baggage.asMap.asScala.foreach { case (key, entry) =>
      span.setAttribute(s"baggage.$key", entry.getValue)
    }
  }

  private def handleResult(result: RequestResult[Identity], span: Span): Unit = {
    result match {
      case RequestResult.Response(response) =>
        val statusCode = response.code.code
        span.setAttribute("http.status_code", statusCode)
        
        if (config.errorPredicate(statusCode)) {
          span.setStatus(StatusCode.ERROR)
        } else {
          span.setStatus(StatusCode.OK)
        }
        
      case RequestResult.Failure(e) =>
        span.setStatus(StatusCode.ERROR)
        span.recordException(e)
    }
  }

  def currentSpan(): Option[Span] = Option(Context.current().get(SERVER_SPAN_KEY))
}
