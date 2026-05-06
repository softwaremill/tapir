package sttp.tapir.server.tracing.ziotel

import sttp.model.headers.{Forwarded, Host}
import sttp.model.{HeaderNames, StatusCode}
import sttp.tapir.AnyEndpoint
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.model.ServerResponse

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.semconv.HttpAttributes
import io.opentelemetry.semconv.UrlAttributes
import io.opentelemetry.semconv.ServerAttributes
import io.opentelemetry.semconv.ErrorAttributes
import scala.annotation.nowarn
import zio.telemetry.opentelemetry.tracing.propagation.TraceContextPropagator
import zio.telemetry.opentelemetry.context.IncomingContextCarrier

/** Configuration for OpenTelemetry Otel4z tracing of server requests, used by [[ZIOtelTracing]]. Use the [[apply]] method to override only
  * some of the configuration options, while using the defaults for the rest.
  *
  * The default values follow OpenTelemetry semantic conventions, as described in [their
  * documentation](https://opentelemetry.io/docs/specs/semconv/http/http-spans/#name).
  *
  * @param tracing
  *   The tracing instance to use. To obtain it see
  *
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
case class ZIOtelTracingConfig(
    propagator: TraceContextPropagator,
    carrier: IncomingContextCarrier[
      scala.collection.mutable.Map[String, String]
    ],

    spanName: ServerRequest => String,
    requestAttributes: ServerRequest => Attributes,
    spanNameFromEndpointAndAttributes: (ServerRequest, AnyEndpoint) => (
        String,
        Attributes
    ),
    responseAttributes: (ServerRequest, ServerResponse[?]) => Attributes,
    errorAttributes: Either[StatusCode, Throwable] => Attributes
)

object ZIOtelTracingConfig {
  def apply(
      propagator: TraceContextPropagator = TraceContextPropagator.default,
      carrier: IncomingContextCarrier[
        scala.collection.mutable.Map[String, String]
      ] = IncomingContextCarrier.default(),
      spanName: ServerRequest => String = Defaults.spanName,
      requestAttributes: ServerRequest => Attributes = Defaults.requestAttributes,
      spanNameFromEndpointAndAttributes: (ServerRequest, AnyEndpoint) => (
          String,
          Attributes
      ) = Defaults.spanNameFromEndpointAndAttributes,
      responseAttributes: (ServerRequest, ServerResponse[?]) => Attributes = Defaults.responseAttributes,
      errorAttributes: Either[StatusCode, Throwable] => Attributes = Defaults.errorAttributes
  ): ZIOtelTracingConfig =
    new ZIOtelTracingConfig(
      propagator,
      carrier,
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
    def spanNameFromEndpointAndAttributes(
        request: ServerRequest,
        endpoint: AnyEndpoint
    ): (String, Attributes) = {
      val route = endpoint.showPathTemplate(showQueryParam = None)
      val name = s"${request.method.method} $route"
      (name, Attributes.of(HttpAttributes.HTTP_ROUTE, route))
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

      Attributes.of(
        HttpAttributes.HTTP_REQUEST_METHOD,
        request.method.method,
        UrlAttributes.URL_PATH,
        request.uri.pathToString,
        UrlAttributes.URL_SCHEME,
        request.uri.scheme.getOrElse("http"),
        ServerAttributes.SERVER_ADDRESS,
        host
      )

    }

    def spanName(request: ServerRequest): String = s"${request.method.method}"

    @nowarn
    def responseAttributes(
        request: ServerRequest,
        response: ServerResponse[_]
    ): Attributes =
      Attributes.of(
        HttpAttributes.HTTP_RESPONSE_STATUS_CODE,
        response.code.code.toLong.asInstanceOf[java.lang.Long]
      )

    def errorAttributes(error: Either[StatusCode, Throwable]): Attributes =
      error match {
        case Left(statusCode) =>
          // see footnote for error.type
          Attributes.of(ErrorAttributes.ERROR_TYPE, statusCode.code.toString)
        case Right(exception) =>
          val errorType = exception.getClass.getSimpleName
          Attributes.of(ErrorAttributes.ERROR_TYPE, errorType)
      }
  }
}
