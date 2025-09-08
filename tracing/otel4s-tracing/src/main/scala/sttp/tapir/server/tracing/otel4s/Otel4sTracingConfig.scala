package sttp.tapir.server.tracing.otel4s

import org.typelevel.otel4s.semconv.attributes.{ErrorAttributes, HttpAttributes, ServerAttributes, UrlAttributes}
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
    errorAttributes: Either[StatusCode, Throwable] => Attributes
)

object Otel4sTracingConfig {
  def apply[F[_]](
      tracer: Tracer[F],
      spanName: ServerRequest => String = Defaults.spanName,
      requestAttributes: ServerRequest => Attributes = Defaults.requestAttributes,
      spanNameFromEndpointAndAttributes: (ServerRequest, AnyEndpoint) => (String, Attributes) = Defaults.spanNameFromEndpointAndAttributes,
      responseAttributes: (ServerRequest, ServerResponse[_]) => Attributes = Defaults.responseAttributes,
      errorAttributes: Either[StatusCode, Throwable] => Attributes = Defaults.errorAttributes
  ): Otel4sTracingConfig[F] =
    new Otel4sTracingConfig(
      tracer,
      spanName,
      requestAttributes,
      spanNameFromEndpointAndAttributes,
      responseAttributes,
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
      (name, Attributes(HttpAttributes.HttpRoute(route)))
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
        HttpAttributes.HttpRequestMethod(request.method.method),
        UrlAttributes.UrlPath(request.uri.pathToString),
        UrlAttributes.UrlScheme(request.uri.scheme.getOrElse("http")),
        ServerAttributes.ServerAddress(host)
      )
    }

    def spanName(request: ServerRequest): String = s"${request.method.method}"

    def responseAttributes(request: ServerRequest, response: ServerResponse[_]): Attributes =
      Attributes(HttpAttributes.HttpResponseStatusCode(response.code.code.toLong))

    def errorAttributes(e: Either[StatusCode, Throwable]): Attributes = Attributes(e match {
      case Left(statusCode) =>
        // see footnote for error.type
        ErrorAttributes.ErrorType(statusCode.code.toString)
      case Right(exception) =>
        val errorType = exception.getClass.getSimpleName
        ErrorAttributes.ErrorType(errorType)
    })
  }
}
