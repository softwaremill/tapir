package sttp.tapir.server.interceptor.metrics

import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.metrics.{EndpointMetric, Metric}
import sttp.tapir.model.{ServerRequest, ServerResponse}
import sttp.tapir.server.interceptor._
import sttp.tapir.server.interpreter.BodyListener
import sttp.tapir.server.interpreter.BodyListenerSyntax._
import sttp.tapir.{DecodeResult, Endpoint}

import scala.util.{Success, Try}

class MetricsRequestInterceptor[F[_], B](metrics: List[Metric[F, _]], ignoreEndpoints: Seq[Endpoint[_, _, _, _]])
    extends RequestInterceptor[F, B] {

  override def apply(responder: Responder[F, B], requestHandler: EndpointInterceptor[F, B] => RequestHandler[F, B]): RequestHandler[F, B] =
    new RequestHandler[F, B] {
      override def apply(request: ServerRequest)(implicit monad: MonadError[F]): F[Option[ServerResponse[B]]] =
        metrics
          .foldLeft(monad.unit(List.empty[EndpointMetric[F]])) { (mAcc, metric) =>
            for {
              metrics <- mAcc
              endpointMetric <- metric match {
                case Metric(m, onRequest) => onRequest(request, m, monad)
              }
            } yield metrics :+ endpointMetric
          }
          .flatMap { endpointMetrics =>
            requestHandler(new MetricsEndpointInterceptor[F, B](endpointMetrics, ignoreEndpoints)).apply(request)
          }
    }
}

private[metrics] class MetricsEndpointInterceptor[F[_], B](
    endpointMetrics: List[EndpointMetric[F]],
    ignoreEndpoints: Seq[Endpoint[_, _, _, _]]
) extends EndpointInterceptor[F, B] {

  override def apply(responder: Responder[F, B], endpointHandler: EndpointHandler[F, B]): EndpointHandler[F, B] =
    new EndpointHandler[F, B] {
      override def onDecodeSuccess[I](
          ctx: DecodeSuccessContext[F, I]
      )(implicit monad: MonadError[F], bodyListener: BodyListener[F, B]): F[ServerResponse[B]] = {
        if (ignoreEndpoints.contains(ctx.endpoint)) endpointHandler.onDecodeSuccess(ctx)
        else {
          val responseWithMetrics: F[ServerResponse[B]] = for {
            _ <- collectMetrics { case EndpointMetric(Some(onRequest), _, _) => onRequest(ctx.endpoint) }
            response <- endpointHandler.onDecodeSuccess(ctx)
            withMetrics <- withBodyOnComplete(response) {
              case Success(_) => collectMetrics { case EndpointMetric(_, Some(onResponse), _) => onResponse(ctx.endpoint, response) }
              case _          => ().unit
            }
          } yield withMetrics

          responseWithMetrics.handleError { case e: Exception =>
            collectMetrics { case EndpointMetric(_, _, Some(onException)) => onException(ctx.endpoint, e) }.flatMap(_ => monad.error(e))
          }
        }
      }

      /** If there's some `ServerResponse` collects `onResponse` as well as `onRequest` metric which was not collected in `onDecodeSuccess` stage.
        */
      override def onDecodeFailure(
          ctx: DecodeFailureContext
      )(implicit monad: MonadError[F], bodyListener: BodyListener[F, B]): F[Option[ServerResponse[B]]] = {
        if (ignoreEndpoints.contains(ctx.endpoint)) endpointHandler.onDecodeFailure(ctx)
        else {
          ctx.failure match {
            case _: DecodeResult.Mismatch => endpointHandler.onDecodeFailure(ctx)
            case _ =>
              val responseWithMetrics: F[Option[ServerResponse[B]]] = for {
                response <- endpointHandler.onDecodeFailure(ctx)
                withMetrics <- response match {
                  case Some(response) =>
                    for {
                      _ <- collectMetrics { case EndpointMetric(Some(onRequest), _, _) => onRequest(ctx.endpoint) }
                      res <- withBodyOnComplete(response) {
                        case Success(_) =>
                          collectMetrics { case EndpointMetric(_, Some(onResponse), _) => onResponse(ctx.endpoint, response) }
                        case _ => ().unit
                      }
                    } yield Some(res)
                  case None => monad.unit(None)
                }
              } yield withMetrics

              responseWithMetrics.handleError { case e: Exception =>
                collectMetrics { case EndpointMetric(_, _, Some(onException)) => onException(ctx.endpoint, e) }.flatMap(_ => monad.error(e))
              }
          }
        }
      }
    }

  private def collectMetrics(pf: PartialFunction[EndpointMetric[F], F[Unit]])(implicit monad: MonadError[F]): F[Unit] = {
    def collect(metrics: List[EndpointMetric[F]]): F[Unit] = {
      metrics match {
        case Nil                            => ().unit
        case m :: tail if pf.isDefinedAt(m) => pf(m).flatMap(_ => collect(tail))
        case _ :: tail                      => collect(tail)
      }
    }
    collect(endpointMetrics)
  }

  private def withBodyOnComplete(
      sr: ServerResponse[B]
  )(cb: Try[Unit] => F[Unit])(implicit monad: MonadError[F], bodyListener: BodyListener[F, B]): F[ServerResponse[B]] =
    sr match {
      case sr @ ServerResponse(_, _, Some(body)) => body.onComplete(cb).map(b => sr.copy(body = Some(b)))
      case sr @ ServerResponse(_, _, None)       => cb(Success(())).map(_ => sr)
    }

}
