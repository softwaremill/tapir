package sttp.tapir.server.interceptor.metrics

import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.Endpoint
import sttp.tapir.metrics.{EndpointMetric, Metric}
import sttp.tapir.model.ServerResponse
import sttp.tapir.server.interceptor._
import sttp.tapir.server.interpreter.BodyListener
import sttp.tapir.server.interpreter.BodyListener._

import scala.util.{Failure, Success, Try}

class MetricsRequestInterceptor[F[_]](metrics: List[Metric[F, _]], ignoreEndpoints: Seq[Endpoint[_, _, _, _]])
    extends RequestInterceptor[F] {

  override def apply[B](responder: Responder[F, B], requestHandler: EndpointInterceptor[F] => RequestHandler[F, B]): RequestHandler[F, B] =
    RequestHandler.from { (request, monad) =>
      implicit val m: MonadError[F] = monad
      metrics
        .foldLeft(List.empty[EndpointMetric[F]].unit) { (mAcc, metric) =>
          for {
            metrics <- mAcc
            endpointMetric <- metric match {
              case Metric(m, onRequest) => onRequest(request, m, monad)
            }
          } yield endpointMetric :: metrics
        }
        .flatMap { endpointMetrics =>
          requestHandler(new MetricsEndpointInterceptor[F](endpointMetrics.reverse, ignoreEndpoints)).apply(request)
        }
    }
}

private[metrics] class MetricsEndpointInterceptor[F[_]](
    endpointMetrics: List[EndpointMetric[F]],
    ignoreEndpoints: Seq[Endpoint[_, _, _, _]]
) extends EndpointInterceptor[F] {

  override def apply[B](responder: Responder[F, B], endpointHandler: EndpointHandler[F, B]): EndpointHandler[F, B] =
    new EndpointHandler[F, B] {
      override def onDecodeSuccess[I](
          ctx: DecodeSuccessContext[F, I]
      )(implicit monad: MonadError[F], bodyListener: BodyListener[F, B]): F[ServerResponse[B]] = {
        if (ignoreEndpoints.contains(ctx.endpoint)) endpointHandler.onDecodeSuccess(ctx)
        else {
          val responseWithMetrics: F[ServerResponse[B]] = for {
            _ <- collectMetrics { case EndpointMetric(Some(onRequest), _, _) => onRequest(ctx.endpoint) }
            response <- endpointHandler.onDecodeSuccess(ctx)
            withMetrics <- withBodyOnComplete(ctx.endpoint, response)
          } yield withMetrics

          responseWithMetrics.handleError { case ex: Exception =>
            collectMetrics { case EndpointMetric(_, _, Some(onException)) => onException(ctx.endpoint, ex) }.flatMap(_ => monad.error(ex))
          }
        }
      }

      /** If there's some `ServerResponse` collects `onResponse` as well as `onRequest` metric which was not collected in `onDecodeSuccess`
        * stage.
        */
      override def onDecodeFailure(
          ctx: DecodeFailureContext
      )(implicit monad: MonadError[F], bodyListener: BodyListener[F, B]): F[Option[ServerResponse[B]]] = {
        if (ignoreEndpoints.contains(ctx.endpoint)) endpointHandler.onDecodeFailure(ctx)
        else {
          val responseWithMetrics: F[Option[ServerResponse[B]]] = for {
            response <- endpointHandler.onDecodeFailure(ctx)
            withMetrics <- response match {
              case Some(response) =>
                for {
                  _ <- collectMetrics { case EndpointMetric(Some(onRequest), _, _) => onRequest(ctx.endpoint) }
                  res <- withBodyOnComplete(ctx.endpoint, response)
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

  private def collectMetrics(pf: PartialFunction[EndpointMetric[F], F[Unit]])(implicit monad: MonadError[F]): F[Unit] = {
    def sequence(metrics: List[EndpointMetric[F]]): F[Unit] = {
      metrics match {
        case Nil                            => ().unit
        case m :: tail if pf.isDefinedAt(m) => pf(m).flatMap(_ => sequence(tail))
        case _ :: tail                      => sequence(tail)
      }
    }
    sequence(endpointMetrics)
  }

  private def withBodyOnComplete[B](endpoint: Endpoint[_, _, _, _], sr: ServerResponse[B])(implicit
      monad: MonadError[F],
      bodyListener: BodyListener[F, B]
  ): F[ServerResponse[B]] = {
    val cb: Try[Unit] => F[Unit] = {
      case Success(_) =>
        collectMetrics { case EndpointMetric(_, Some(onResponse), _) => onResponse(endpoint, sr) }
      case Failure(ex) =>
        collectMetrics { case EndpointMetric(_, _, Some(onException)) => onException(endpoint, ex) }.flatMap(_ => monad.error(ex))
    }

    sr match {
      case sr @ ServerResponse(_, _, Some(body)) => body.onComplete(cb).map(b => sr.copy(body = Some(b)))
      case sr @ ServerResponse(_, _, None)       => cb(Success(())).map(_ => sr)
    }
  }

}
