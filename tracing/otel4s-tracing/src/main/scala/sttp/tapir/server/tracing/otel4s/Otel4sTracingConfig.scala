package sttp.tapir.server.tracing.otel4s

import io.opentelemetry.semconv.{ErrorAttributes, HttpAttributes, ServerAttributes, UrlAttributes}
import org.typelevel.otel4s.trace.Tracer
import org.typelevel.otel4s.{Attribute, Attributes}
import sttp.model.headers.{Forwarded, Host}
import sttp.model.{HeaderNames, StatusCode}
import sttp.tapir.AnyEndpoint
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.model.ServerResponse

/** Configuration for OpenTelemetry Otel4s tracing of server requests, used by [[Otel4sTracing]]. Use the [[apply]] method to override only
  * some of the configuration options, while using the defaults for the rest.
  *
  * The default values follow OpenTelemetry semantic conventions, as described in [their
  * documentation](https://opentelemetry.io/docs/specs/semconv/http/http-spans/#name).
  *
  * @param tracer
  *   The tracer instance to use. To obtain it see https://typelevel.org/otel4s/#getting-started
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
case class Otel4sTracingConfig[F[_]](
    tracer: Tracer[F],
    spanName: ServerRequest => String,
    requestAttributes: ServerRequest => Attributes,
    spanNameFromEndpointAndAttributes: (ServerRequest, AnyEndpoint) => (String, Attributes),
    responseAttributes: (ServerRequest, ServerResponse[_]) => Attributes,
    noEndpointsMatchAttributes: Attributes,
    errorAttributes: Either[StatusCode, Throwable] => Attributes
)

object Otel4sTracingConfig {
  def apply[F[_]](
      tracer: Tracer[F],
      spanName: ServerRequest => String = Defaults.spanName,
      requestAttributes: ServerRequest => Attributes = Defaults.requestAttributes,
      spanNameFromEndpointAndAttributes: (ServerRequest, AnyEndpoint) => (String, Attributes) = Defaults.spanNameFromEndpointAndAttributes,
      responseAttributes: (ServerRequest, ServerResponse[_]) => Attributes = Defaults.responseAttributes,
      noEndpointsMatchAttributes: Attributes = Defaults.noEndpointsMatchAttributes,
      errorAttributes: Either[StatusCode, Throwable] => Attributes = Defaults.errorAttributes
  ): Otel4sTracingConfig[F] =
    new Otel4sTracingConfig(
      tracer,
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
    def spanNameFromEndpointAndAttributes(request: ServerRequest, endpoint: AnyEndpoint): (String, Attributes) = {
      val route = endpoint.showPathTemplate(showQueryParam = None)
      val name = s"${request.method.method} $route"
      (name, Attributes(Attribute(HttpAttributes.HTTP_ROUTE.getKey, route)))
    }

    def requestAttributes(request: ServerRequest): Attributes = {
      val hostHeader: String = request
        .header(HeaderNames.Forwarded)
        .flatMap(f => Forwarded.parse(f).toOption.flatMap(_.headOption).flatMap(_.host))
        .orElse(request.header(HeaderNames.XForwardedHost))
        .orElse(request.header(":authority"))
        .orElse(request.header(HeaderNames.Host))
        .getOrElse("unknown")

      val (host, _) = Host.parseHostAndPort(hostHeader)

      Attributes(
        Attribute(HttpAttributes.HTTP_REQUEST_METHOD.getKey, request.method.method),
        Attribute(UrlAttributes.URL_PATH.getKey, request.uri.pathToString),
        Attribute(UrlAttributes.URL_SCHEME.getKey, request.uri.scheme.getOrElse("http")),
        Attribute(ServerAttributes.SERVER_ADDRESS.getKey, host)
      )
    }

    def spanName(request: ServerRequest): String = s"${request.method.method}"

    def responseAttributes(request: ServerRequest, response: ServerResponse[_]): Attributes =
      Attributes(Attribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE.getKey, response.code.code.toLong))

    val noEndpointsMatchAttributes: Attributes =
      Attributes(Attribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE.getKey, StatusCode.NotFound.code.toLong))

    def errorAttributes(e: Either[StatusCode, Throwable]): Attributes = Attributes(e match {
      case Left(statusCode) =>
        // see footnote for error.type
        Attribute(ErrorAttributes.ERROR_TYPE.getKey, statusCode.code.toString)
      case Right(exception) =>
        val errorType = exception.getClass.getSimpleName
        Attribute(ErrorAttributes.ERROR_TYPE.getKey, errorType)
    })
  }
}
