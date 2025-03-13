package sttp.tapir.server.tracing.opentelemetry

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.{Attributes, AttributesBuilder}
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.semconv.{ErrorAttributes, HttpAttributes, ServerAttributes, UrlAttributes}
import sttp.model.headers.{Forwarded, Host}
import sttp.model.{HeaderNames, StatusCode}
import sttp.tapir.AnyEndpoint
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.model.ServerResponse

/** Configuration for OpenTelemetry tracing of server requests, used by [[OpenTelemetryTracing]]. Use one of the [[apply]] methods to
  * override only some of the configuration options, while using the defaults for the rest.
  *
  * The default values follow OpenTelemetry semantic conventions, as described in [their
  * documentation](https://opentelemetry.io/docs/specs/semconv/http/http-spans/#name).
  *
  * @param tracer
  *   The tracer instance to use. To obtain a default one using an [[OpenTelemetry]] instance, use the appropriate [[apply]] variant.
  * @param propagators
  *   The context propagators to use. To obtain a default one using an [[OpenTelemetry]] instance, use the appropriate [[apply]] variant.
  * @param spanName
  *   Calculates the name of the span, given an incoming request.
  * @param requestAttributes
  *   Calculates the attributes of the span, given an incoming request.
  * @param spanNameFromEndpointAndAttributes
  *   Calculates an updated name of the span and additional attributes, once (and if) an endpoint is determined to handle the request. By
  *   default, the span name includes the request's method and the route, which is created by rendering the endpoint's path template.
  * @param responseAttributes
  *   Calculates additional attributes of the span, given a response that will be sent back.
  * @param noEndpointsMatchAttributes
  *   Calculates additional attributes of the span if no endpoints match the incoming request. By default, this sets the response status to
  *   404, which mirrors the behavior of most servers. If there are other routes matched by the server after Tapir determines that the
  *   request matched to any endpoint, this should be set to an empty set of attributes.
  * @param errorAttributes
  *   Calculates additional attributes of the span, given an error that occurred while processing the request (an exception); although
  *   usually, exceptions are translated into 5xx responses earlier in the interceptor chain.
  */
case class OpenTelemetryTracingConfig(
    tracer: Tracer,
    propagators: ContextPropagators,
    spanName: ServerRequest => String,
    requestAttributes: ServerRequest => Attributes,
    spanNameFromEndpointAndAttributes: (ServerRequest, AnyEndpoint) => (String, Attributes),
    responseAttributes: (ServerRequest, ServerResponse[_]) => Attributes,
    noEndpointsMatchAttributes: Attributes,
    errorAttributes: Either[StatusCode, Throwable] => Attributes
)

object OpenTelemetryTracingConfig {
  def apply(
      openTelemetry: OpenTelemetry,
      spanName: ServerRequest => String = Defaults.spanName _,
      requestAttributes: ServerRequest => Attributes = Defaults.requestAttributes _,
      spanNameFromEndpointAndAttributes: (ServerRequest, AnyEndpoint) => (String, Attributes) =
        Defaults.spanNameFromEndpointAndAttributes _,
      responseAttributes: (ServerRequest, ServerResponse[_]) => Attributes = Defaults.responseAttributes _,
      noEndpointsMatchAttributes: Attributes = Defaults.noEndpointsMatchAttributes,
      errorAttributes: Either[StatusCode, Throwable] => Attributes = Defaults.errorAttributes _
  ): OpenTelemetryTracingConfig = usingTracer(
    openTelemetry.tracerBuilder(Defaults.instrumentationScopeName).setInstrumentationVersion(Defaults.instrumentationScopeVersion).build(),
    openTelemetry.getPropagators(),
    spanName = spanName,
    requestAttributes = requestAttributes,
    spanNameFromEndpointAndAttributes = spanNameFromEndpointAndAttributes,
    responseAttributes = responseAttributes,
    noEndpointsMatchAttributes = noEndpointsMatchAttributes,
    errorAttributes = errorAttributes
  )

  def usingTracer(
      tracer: Tracer,
      propagators: ContextPropagators,
      spanName: ServerRequest => String = Defaults.spanName _,
      requestAttributes: ServerRequest => Attributes = Defaults.requestAttributes _,
      spanNameFromEndpointAndAttributes: (ServerRequest, AnyEndpoint) => (String, Attributes) =
        Defaults.spanNameFromEndpointAndAttributes _,
      responseAttributes: (ServerRequest, ServerResponse[_]) => Attributes = Defaults.responseAttributes _,
      noEndpointsMatchAttributes: Attributes = Defaults.noEndpointsMatchAttributes,
      errorAttributes: Either[StatusCode, Throwable] => Attributes = Defaults.errorAttributes _
  ): OpenTelemetryTracingConfig =
    new OpenTelemetryTracingConfig(
      tracer,
      propagators,
      spanName,
      requestAttributes,
      spanNameFromEndpointAndAttributes,
      responseAttributes,
      noEndpointsMatchAttributes,
      errorAttributes
    )

  /** @see
    *   https://opentelemetry.io/docs/specs/semconv/http/http-spans/#name
    * @see
    *   https://opentelemetry.io/docs/specs/semconv/http/http-spans/#http-server
    */
  object Defaults {

    def spanName(request: ServerRequest): String = s"${request.method.method}"

    def requestAttributes(request: ServerRequest): Attributes = requestAttributesBuilder(request).build()

    def spanNameFromEndpointAndAttributes(request: ServerRequest, endpoint: AnyEndpoint): (String, Attributes) = {
      val route = endpoint.showPathTemplate(showQueryParam = None)
      val name = s"${request.method.method} $route"
      (name, Attributes.builder.put(HttpAttributes.HTTP_ROUTE, route).build())
    }

    private def requestAttributesBuilder(request: ServerRequest): AttributesBuilder = {
      val hostHeader: String =
        request
          .header(HeaderNames.Forwarded)
          .flatMap(f => Forwarded.parse(f).toOption.flatMap(_.headOption).flatMap(_.host))
          .orElse(request.header(HeaderNames.XForwardedHost))
          .orElse(request.header(":authority"))
          .orElse(request.header(HeaderNames.Host))
          .getOrElse("unknown")

      val (host, port) = Host.parseHostAndPort(hostHeader)

      val builder = Attributes.builder
        .put(HttpAttributes.HTTP_REQUEST_METHOD, request.method.method)
        .put(UrlAttributes.URL_PATH, request.uri.pathToString)
        .put(UrlAttributes.URL_SCHEME, request.uri.scheme.getOrElse("http"))
        .put(ServerAttributes.SERVER_ADDRESS, host)

      port.foreach(p => builder.put(ServerAttributes.SERVER_PORT, p.toLong: java.lang.Long))

      builder
    }

    def responseAttributes(request: ServerRequest, response: ServerResponse[_]): Attributes =
      Attributes.builder
        .put(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, response.code.code.toLong: java.lang.Long)
        .build()

    val noEndpointsMatchAttributes: Attributes =
      Attributes.builder
        .put(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, StatusCode.NotFound.code.toLong: java.lang.Long)
        .build()

    def errorAttributes(e: Either[StatusCode, Throwable]): Attributes = {
      e match {
        case Left(statusCode) =>
          // see footnote for error.type
          Attributes.builder().put(ErrorAttributes.ERROR_TYPE, statusCode.code.toString).build()
        case Right(exception) => {
          val errorType = exception.getClass.getSimpleName
          Attributes.builder().put(ErrorAttributes.ERROR_TYPE, errorType).build()
        }
      }

    }

    val instrumentationScopeName = "tapir"

    val instrumentationScopeVersion = "1.0.0"
  }
}
