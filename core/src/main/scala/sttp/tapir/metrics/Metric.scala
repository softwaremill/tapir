package sttp.tapir.metrics

import sttp.tapir.Endpoint
import sttp.tapir.model.{ServerRequest, ServerResponse}

case class Metric[F[_], M](
    metric: M,
    onRequest: Option[(Endpoint[_, _, _, _], ServerRequest, M) => F[Unit]] = None,
    onResponse: Option[(Endpoint[_, _, _, _], ServerRequest, ServerResponse[_], M) => F[Unit]] = None
) {
  def onRequest(f: (Endpoint[_, _, _, _], ServerRequest, M) => F[Unit]): Metric[F, M] = copy(onRequest = Some(f))
  def onResponse(f: (Endpoint[_, _, _, _], ServerRequest, ServerResponse[_], M) => F[Unit]): Metric[F, M] = copy(onResponse = Some(f))
}
