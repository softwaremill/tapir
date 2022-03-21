package sttp.tapir.server.metrics

import sttp.monad.MonadError
import sttp.tapir.AnyEndpoint
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.model.ServerResponse

case class Metric[F[_], M](
    metric: M,
    /** Called when the request starts. */
    onRequest: (ServerRequest, M, MonadError[F]) => F[EndpointMetric[F]]
)

case class EndpointMetric[F[_]](
    /** Called when an endpoint matches the request, before calling the server logic. */
    onEndpointRequest: Option[AnyEndpoint => F[Unit]] = None,
    /** Called when the response headers are ready (not necessarily the whole response body). */
    onResponseHeaders: Option[(AnyEndpoint, ServerResponse[_]) => F[Unit]] = None,
    /** Called when the response body is complete. */
    onResponseBody: Option[(AnyEndpoint, ServerResponse[_]) => F[Unit]] = None,
    onException: Option[(AnyEndpoint, Throwable) => F[Unit]] = None
) {
  def onEndpointRequest(f: AnyEndpoint => F[Unit]): EndpointMetric[F] = this.copy(onEndpointRequest = Some(f))
  def onResponseHeaders(f: (AnyEndpoint, ServerResponse[_]) => F[Unit]): EndpointMetric[F] = this.copy(onResponseHeaders = Some(f))
  def onResponseBody(f: (AnyEndpoint, ServerResponse[_]) => F[Unit]): EndpointMetric[F] = this.copy(onResponseBody = Some(f))
  def onException(f: (AnyEndpoint, Throwable) => F[Unit]): EndpointMetric[F] = this.copy(onException = Some(f))
}

case class ResponsePhaseLabel(name: String, headersValue: String, bodyValue: String)
case class MetricLabels(
    forRequest: List[(String, (AnyEndpoint, ServerRequest) => String)],
    forResponse: List[(String, Either[Throwable, ServerResponse[_]] => String)],
    forResponsePhase: ResponsePhaseLabel = ResponsePhaseLabel("phase", "headers", "body")
) {
  def namesForRequest: List[String] = forRequest.map { case (name, _) => name }
  def namesForResponse: List[String] = forResponse.map { case (name, _) => name }

  def valuesForRequest(ep: AnyEndpoint, req: ServerRequest): List[String] = forRequest.map { case (_, f) => f(ep, req) }
  def valuesForResponse(res: ServerResponse[_]): List[String] = forResponse.map { case (_, f) => f(Right(res)) }
  def valuesForResponse(ex: Throwable): List[String] = forResponse.map { case (_, f) => f(Left(ex)) }
}

object MetricLabels {

  /** Labels request by path and method, response by status code */
  lazy val Default: MetricLabels = MetricLabels(
    forRequest = List(
      "path" -> { case (ep, _) => ep.showPathTemplate(showQueryParam = None) },
      "method" -> { case (_, req) => req.method.method }
    ),
    forResponse = List(
      "status" -> {
        case Right(r) =>
          r.code match {
            case c if c.isInformational => "1xx"
            case c if c.isSuccess       => "2xx"
            case c if c.isRedirect      => "3xx"
            case c if c.isClientError   => "4xx"
            case c if c.isServerError   => "5xx"
            case _                      => ""
          }
        case Left(_) => "5xx"
      }
    )
  )
}
