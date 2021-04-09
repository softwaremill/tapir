package sttp.tapir.metrics

import sttp.tapir.model.{ServerRequest, ServerResponse}

case class Metric[F[_], M](
    metric: M,
    onRequest: Option[(ServerRequest, M) => F[Unit]] = None,
    onResponse: Option[(ServerRequest, ServerResponse[_], M) => F[Unit]] = None,
    onDecodeFailure: Option[(ServerRequest, M) => F[Unit]] = None
) {
  def onRequest(f: (ServerRequest, M) => F[Unit]): Metric[F, M] = copy(onRequest = Some(f))
  def onResponse(f: (ServerRequest, ServerResponse[_], M) => F[Unit]): Metric[F, M] = copy(onResponse = Some(f))
  def onDecodeFailure(f: (ServerRequest, M) => F[Unit]): Metric[F, M] =
    copy(onDecodeFailure = Some(f))
}
