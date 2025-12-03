package sttp.tapir.server.interceptor.metrics

import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.AnyEndpoint
import sttp.tapir.server.interceptor._
import sttp.tapir.server.interpreter.BodyListener
import sttp.tapir.server.interpreter.BodyListener._
import sttp.tapir.server.metrics.{EndpointMetric, Metric}
import sttp.tapir.server.model.ServerResponse

import scala.util.{Failure, Success, Try}

class MetricsRequestInterceptor[F[_]](metrics: List[Metric[F, ?]], ignoreEndpoints: Seq[AnyEndpoint]) extends RequestInterceptor[F] {

  override def apply[R, B](
      responder: Responder[F, B],
      requestHandler: EndpointInterceptor[F] => RequestHandler[F, R, B]
  ): RequestHandler[F, R, B] =
    RequestHandler.from { (request, endpoints, monad) =>
      implicit val m: MonadError[F] = monad
      println("REQUEST")
      metrics
        .foldLeft(List.empty[EndpointMetric[F]].unit) { (mAcc, metric) =>
          for {
            metrics <- mAcc
            endpointMetric <- metric match {
              case Metric(m, onRequest) => onRequest(request, m, monad)
            }
          } yield endpointMetric :: metrics
        }
        .flatMap { _endpointMetrics =>
          println("XYZ")
          val endpointMetrics = _endpointMetrics.reverse
          val delegate = requestHandler(new MetricsEndpointInterceptor[F](endpointMetrics, ignoreEndpoints))
          delegate(request, endpoints).flatTap {
            case RequestResult.Response(response, ResponseSource.RequestHandler) =>
              collectRequestHandlerResponseMetrics(endpointMetrics, response)
            case RequestResult.Response(response, ResponseSource.EndpointHandler) => ().unit // already handled
            case f @ RequestResult.Failure(_)                                     =>
              println("ABC " + f)
              collectDecodeFailureMetrics(endpointMetrics)
          }
        }
    }

  private def collectRequestHandlerResponseMetrics[B](
      endpointMetrics: List[EndpointMetric[F]],
      response: ServerResponse[B]
  )(implicit monad: MonadError[F]): F[Unit] = {
    def sequence(metrics: List[EndpointMetric[F]]): F[Unit] = {
      metrics match {
        case Nil                                                        => ().unit
        case EndpointMetric(_, _, _, _, Some(onInterceptor), _) :: tail => onInterceptor(response).flatMap(_ => sequence(tail))
        case _ :: tail                                                  => sequence(tail)
      }
    }
    sequence(endpointMetrics)
  }

  private def collectDecodeFailureMetrics(
      endpointMetrics: List[EndpointMetric[F]]
  )(implicit monad: MonadError[F]): F[Unit] = {
    def sequence(metrics: List[EndpointMetric[F]]): F[Unit] = {
      metrics match {
        case Nil                                                    => ().unit
        case EndpointMetric(_, _, _, _, _, Some(onFailure)) :: tail => onFailure().flatMap(_ => sequence(tail))
        case _ :: tail                                              => sequence(tail)
      }
    }
    sequence(endpointMetrics)
  }
}

private[metrics] class MetricsEndpointInterceptor[F[_]](
    endpointMetrics: List[EndpointMetric[F]],
    ignoreEndpoints: Seq[AnyEndpoint]
) extends EndpointInterceptor[F] {

  override def apply[B](responder: Responder[F, B], endpointHandler: EndpointHandler[F, B]): EndpointHandler[F, B] =
    new EndpointHandler[F, B] {

      override def onDecodeSuccess[A, U, I](
          ctx: DecodeSuccessContext[F, A, U, I]
      )(implicit monad: MonadError[F], bodyListener: BodyListener[F, B]): F[ServerResponse[B]] = {
        if (ignoreEndpoints.contains(ctx.endpoint)) endpointHandler.onDecodeSuccess(ctx)
        else {
          def responseWithMetrics: F[ServerResponse[B]] = for {
            _ <- collectRequestMetrics(ctx.endpoint)
            response <- endpointHandler.onDecodeSuccess(ctx)
            _ <- collectResponseHeadersMetrics(ctx.endpoint, response)
            withMetrics <- withBodyOnComplete(ctx.endpoint, response)
          } yield withMetrics

          handleResponseExceptions(responseWithMetrics, ctx.endpoint)
        }
      }

      /** Collects `onResponse` as well as `onRequest` metric which was not collected in `onDecodeSuccess` stage. */
      override def onSecurityFailure[A](
          ctx: SecurityFailureContext[F, A]
      )(implicit monad: MonadError[F], bodyListener: BodyListener[F, B]): F[ServerResponse[B]] = {
        if (ignoreEndpoints.contains(ctx.endpoint)) endpointHandler.onSecurityFailure(ctx)
        else {
          def responseWithMetrics: F[ServerResponse[B]] = for {
            _ <- collectRequestMetrics(ctx.endpoint)
            response <- endpointHandler.onSecurityFailure(ctx)
            _ <- collectResponseHeadersMetrics(ctx.endpoint, response)
            withMetrics <- withBodyOnComplete(ctx.endpoint, response)
          } yield withMetrics

          handleResponseExceptions(responseWithMetrics, ctx.endpoint)
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
          def responseWithMetrics: F[Option[ServerResponse[B]]] = for {
            response <- endpointHandler.onDecodeFailure(ctx)
            withMetrics <- response match {
              case Some(response) =>
                for {
                  _ <- collectRequestMetrics(ctx.endpoint)
                  _ <- collectResponseHeadersMetrics(ctx.endpoint, response)
                  res <- withBodyOnComplete(ctx.endpoint, response)
                } yield Some(res)
              case None => monad.unit(None)
            }
          } yield withMetrics

          handleResponseExceptions(responseWithMetrics, ctx.endpoint)
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

  private def withBodyOnComplete[B](endpoint: AnyEndpoint, sr: ServerResponse[B])(implicit
      monad: MonadError[F],
      bodyListener: BodyListener[F, B]
  ): F[ServerResponse[B]] = {
    val cb: Try[Unit] => F[Unit] = {
      case Success(_) =>
        collectMetrics { case EndpointMetric(_, _, Some(onResponseBody), _, _, _) => onResponseBody(endpoint, sr) }
      case Failure(ex) =>
        collectExceptionMetrics(endpoint, ex)
    }

    sr match {
      case sr @ ServerResponse(_, _, Some(body), _) => body.onComplete(cb).map(b => sr.copy(body = Some(b)))
      case sr @ ServerResponse(_, _, None, _)       => cb(Success(())).map(_ => sr)
    }
  }

  private def handleResponseExceptions[T](r: => F[T], e: AnyEndpoint)(implicit monad: MonadError[F]): F[T] =
    // we only need to "tap" the error, hence re-throwing after the metrics are collected
    r.handleError { case ex: Exception => collectExceptionMetrics(e, ex).flatMap(_ => monad.error(ex)) }

  private def collectExceptionMetrics[T](e: AnyEndpoint, ex: Throwable)(implicit monad: MonadError[F]): F[Unit] =
    collectMetrics { case EndpointMetric(_, _, _, Some(onException), _, _) => onException(e, ex) }

  private def collectRequestMetrics(endpoint: AnyEndpoint)(implicit monad: MonadError[F]): F[Unit] =
    collectMetrics { case EndpointMetric(Some(onRequest), _, _, _, _, _) => onRequest(endpoint) }

  private def collectResponseHeadersMetrics[B](endpoint: AnyEndpoint, sr: ServerResponse[B])(implicit monad: MonadError[F]): F[Unit] =
    collectMetrics { case EndpointMetric(_, Some(onResponseHeaders), _, _, _, _) => onResponseHeaders(endpoint, sr) }
}
