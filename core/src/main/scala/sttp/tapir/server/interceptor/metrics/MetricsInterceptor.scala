package sttp.tapir.server.interceptor.metrics

import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.metrics.Metric
import sttp.tapir.model.ServerResponse
import sttp.tapir.server.interceptor._
import sttp.tapir.server.interpreter.BodyListener
import sttp.tapir.server.interpreter.BodyListenerSyntax._
import sttp.tapir.{DecodeResult, Endpoint}

class MetricsInterceptor[F[_], B](metrics: List[Metric[F, _]], ignoreEndpoints: Seq[Endpoint[_, _, _, _]])(implicit
    monad: MonadError[F],
    listener: BodyListener[F, B]
) extends EndpointInterceptor[F, B] {

  override def apply(responder: Responder[F, B], endpointHandler: EndpointHandler[F, B]): EndpointHandler[F, B] =
    new EndpointHandler[F, B] {
      override def onDecodeSuccess[I](ctx: DecodeSuccessContext[F, I])(implicit monad: MonadError[F]): F[ServerResponse[B]] =
        if (ignoreEndpoints.contains(ctx.endpoint)) endpointHandler.onDecodeSuccess(ctx)
        else {
          for {
            _ <- collectMetrics { case Metric(m, Some(onRequest), _) => onRequest(ctx.endpoint, ctx.request, m) }
            response <- endpointHandler.onDecodeSuccess(ctx)
          } yield response.copy(body = response.body.map(_.listen {
            collectMetrics { case Metric(m, _, Some(onResponse)) => onResponse(ctx.endpoint, ctx.request, response, m) }
          }))
        }

      /** If there's some `ServerResponse` collects `onResponse` as well as `onRequest` metric which was not collected in `onDecodeSuccess` stage.
        */
      override def onDecodeFailure(ctx: DecodeFailureContext)(implicit monad: MonadError[F]): F[Option[ServerResponse[B]]] =
        if (ignoreEndpoints.contains(ctx.endpoint)) endpointHandler.onDecodeFailure(ctx)
        else {
          ctx.failure match {
            case _: DecodeResult.Mismatch => endpointHandler.onDecodeFailure(ctx)
            case _ =>
              for {
                response <- endpointHandler.onDecodeFailure(ctx)
              } yield response.map(r =>
                r.copy(body = r.body.map(_.listen {
                  for {
                    _ <- collectMetrics { case Metric(m, Some(onRequest), _) => onRequest(ctx.endpoint, ctx.request, m) }
                    _ <- collectMetrics { case Metric(m, _, Some(onResponse)) => onResponse(ctx.endpoint, ctx.request, r, m) }
                  } yield ()
                }))
              )
          }
        }
    }

  private def collectMetrics(pf: PartialFunction[Metric[F, _], F[Unit]]): F[Unit] = {
    def collect(metrics: List[Metric[F, _]]): F[Unit] = {
      metrics match {
        case Nil                            => monad.unit(())
        case m :: tail if pf.isDefinedAt(m) => pf(m).flatMap(_ => collect(tail))
        case _ :: tail                      => collect(tail)
      }
    }
    collect(metrics)
  }

}
