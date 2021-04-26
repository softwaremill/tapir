package sttp.tapir.metrics

import sttp.monad.MonadError
import sttp.tapir.Endpoint
import sttp.tapir.model.{ServerRequest, ServerResponse}

case class Metric[F[_], M](
    metric: M,
    onRequest: (ServerRequest, M, MonadError[F]) => F[EndpointMetric[F]]
)

case class EndpointMetric[F[_]](
    onRequest: Option[Endpoint[_, _, _, _] => F[Unit]] = None,
    onResponse: Option[(Endpoint[_, _, _, _], ServerResponse[_]) => F[Unit]] = None,
    onException: Option[(Endpoint[_, _, _, _], Throwable) => F[Unit]] = None
) {
  def onRequest(f: Endpoint[_, _, _, _] => F[Unit]): EndpointMetric[F] = this.copy(onRequest = Some(f))
  def onResponse(f: (Endpoint[_, _, _, _], ServerResponse[_]) => F[Unit]): EndpointMetric[F] = this.copy(onResponse = Some(f))
  def onException(f: (Endpoint[_, _, _, _], Throwable) => F[Unit]): EndpointMetric[F] = this.copy(onException = Some(f))
}
