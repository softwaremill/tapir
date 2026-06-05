package sttp.tapir.server.ziopentelemetry

import zio._

import collection.mutable.{Map => MutableMap}

import sttp.monad.MonadError
import sttp.model.{StatusCode => SttpStatusCode}
import sttp.tapir.AnyEndpoint
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.RequestResult.{Failure, Response}
import sttp.tapir.server.interceptor._
import sttp.tapir.server.interpreter.BodyListener
import sttp.tapir.server.model.ServerResponse

import io.opentelemetry.api.trace.Span

import zio.telemetry.opentelemetry.tracing.Tracing
import io.opentelemetry.api.trace.SpanKind

import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.common.Attributes
import zio.telemetry.opentelemetry.context.IncomingContextCarrier
import sttp.tapir.server.ziohttp.ZioStreamHttpResponseBody

/** Interceptor which traces requests using ZIO OpenTelemetry.
  *
  * Span names and attributes are calculated using the provided [[ZIOpenTelemetryTracingConfig]].
  *
  * To use, customize the interceptors of the server interpreter you are using, and prepend this interceptor, so that it runs as early as
  * possible, e.g.:
  *
  * {{{
  *  def serverOptions(using
  *        tracing: Tracing
  *    ): ZioHttpServerOptions[Any] =
  *      ZioHttpServerOptions.customiseInterceptors
  *        .prependInterceptor(
  *          ZIOpenTelemetryTracing(tracing)
  *        )
  *        .appendInterceptor(
  *          CORSInterceptor.default
  *        )
  *        .serverLog(
  *          ZioHttpServerOptions.defaultServerLog
  *        )
  *        .options
  * }}}
  */

class ZIOpenTelemetryTracing(
    tracing: Tracing,
    config: ZIOpenTelemetryTracingConfig
) extends RequestInterceptor[Task] {

  import config._

  private def extractCarrier(request: ServerRequest) = {
    val headers = request.headers
    val carrier = MutableMap.empty[String, String]
    headers.foreach(h => carrier.put(h.name, h.value))
    IncomingContextCarrier.default(carrier)
  }

  override def apply[R, B](
      responder: Responder[Task, B],
      requestHandler: EndpointInterceptor[Task] => RequestHandler[Task, R, B]
  ): RequestHandler[Task, R, B] =

    new RequestHandler[Task, R, B] {
      override def apply(
          request: ServerRequest,
          endpoints: List[ServerEndpoint[R, Task]]
      )(implicit monad: MonadError[Task]): Task[RequestResult[B]] = tracing
        .extractSpanUnsafe(
          config.propagator,
          extractCarrier(request),
          request.showShort,
          spanKind = SpanKind.SERVER,
          attributes = config.requestAttributes(request)
        )
        .flatMap { case (span, finalize) =>
          handleRequest(span, finalize, request, endpoints)
            .tapError { e =>
                spanError(span, finalize)(Right(e))
             }
        }

      /** Handle the request, setting span attributes and status based on the result.
        *
        * @param span
        * @param request
        * @param endpoints
        * @param monad
        * @return
        */
      private def handleRequest(
          span: Span,
          finalize: UIO[Any],
          request: ServerRequest,
          endpoints: List[ServerEndpoint[R, Task]]
      )(implicit monad: MonadError[Task]): Task[RequestResult[B]] =
        requestHandler(
          knownEndpointInterceptor(request, span)
        )(request, endpoints).flatMap {
          case Response(response, source) =>
            setSpanAttributes(
              span,
              responseAttributes(request, response)
            ) *> finalizeSpan(response, span, finalize)
              .map { case (response) =>
                Response(response, source)
              }
          case f @ Failure(_) =>
            ZIO.logError(s"Request failed with decode failures: ${f.failures.map(_.failure).mkString(", ")}") *>
              spanError(span, finalize)(Left(SttpStatusCode.BadRequest))
                .as(f)

        }

      /** Finalize the span by setting error status if needed.
        *
        * If the response body is a stream, wrap it in a stream that ensures the finalize effect is run after the stream is consumed. and
        * ensuring the finalize effect is run after the response body is consumed, if there is one.
        *
        * @param response
        * @param span
        * @param finalize
        * @return
        */
      private def finalizeSpan(response: ServerResponse[B], span: Span, finalize: UIO[Any]): Task[ServerResponse[B]] =
        (response.body match {
          case Some(Right(ZioStreamHttpResponseBody(stream, contentLength))) =>
            val wrapped = stream.ensuringWith {
              case Exit.Success(_)     => ZIO.logTrace("Stream completed successfully") *> finalize
              case Exit.Failure(cause) =>
                handleStreamError(cause, span, finalize)
            }
            ZIO.succeed(response.copy(body = Some(Right(ZioStreamHttpResponseBody(wrapped, contentLength)).asInstanceOf[B])))
          case _ =>
            ZIO.when(response.isServerError || response.isClientError)(
              spanError(span, finalize)(Left(response.code))
            ) *> finalize *> ZIO.succeed(response)
        })

      private def handleStreamError(cause: Cause[Any], span: Span, finalize: UIO[Any]): UIO[Unit] =
        cause match {
          case Cause.Fail(error, a) =>
            ZIO.logError(s"Stream failed with error: $error") *> spanError(span, finalize)(Left(SttpStatusCode.InternalServerError))
          case Cause.Interrupt(fiberId, _) =>
            ZIO.logError(s"Stream interrupted by fiber: $fiberId") *> spanError(span, finalize)(Left(SttpStatusCode.InternalServerError))
          case Cause.Die(throwable, stackTrace) =>
            ZIO.logError(s"Stream died with throwable: $throwable") *> spanError(span, finalize)(Left(SttpStatusCode.InternalServerError))
          case _ => ZIO.logError(s"Stream failed with cause: $cause") *> spanError(span, finalize)(Left(SttpStatusCode.InternalServerError))
        }

      /** Interceptor which sets span name and attributes based on the matched endpoint.
        *
        * @param request
        * @param span
        * @return
        */
      def knownEndpointInterceptor(
          request: ServerRequest,
          span: Span
      ) =
        new EndpointInterceptor[Task] {
          def apply[B2](
              responder: Responder[Task, B2],
              endpointHandler: EndpointHandler[Task, B2]
          ): EndpointHandler[Task, B2] = new EndpointHandler[Task, B2] {
            def onDecodeFailure(
                ctx: DecodeFailureContext
            )(implicit
                monad: MonadError[Task],
                bodyListener: BodyListener[Task, B2]
            ): Task[Option[ServerResponse[B2]]] =
              endpointHandler.onDecodeFailure(ctx).flatMap {
                case result @ Some(_) =>
                  knownEndpoint(ctx.endpoint).map(_ => result)
                case None => monad.unit(None)
              }

            def onDecodeSuccess[A, U, I](
                ctx: DecodeSuccessContext[Task, A, U, I]
            )(implicit
                monad: MonadError[Task],
                bodyListener: BodyListener[Task, B2]
            ): Task[ServerResponse[B2]] =
              knownEndpoint(ctx.endpoint).flatMap(_ => endpointHandler.onDecodeSuccess(ctx))

            def onSecurityFailure[A](
                ctx: SecurityFailureContext[Task, A]
            )(implicit
                monad: MonadError[Task],
                bodyListener: BodyListener[Task, B2]
            ): Task[ServerResponse[B2]] =
              knownEndpoint(ctx.endpoint).flatMap(_ => endpointHandler.onSecurityFailure(ctx))

            def knownEndpoint(
                e: AnyEndpoint
            ): Task[Unit] = {
              val (name, attributes) =
                spanNameFromEndpointAndAttributes(request, e)
              ZIO.succeed {
                span
                  .updateName(name)
                span.setAllAttributes(attributes)
              }.unit
            }
          }
        }

      /** Set span status and attributes for errors, both exceptions and error status.
        */
      private def spanError(
          span: Span,
          finalize: UIO[Any]
      )(error: Either[SttpStatusCode, Throwable]): UIO[Unit] =
        ZIO.succeed {
          span.setStatus(StatusCode.ERROR)
          span.setAllAttributes(errorAttributes(error))
        } *> finalize.unit

      private def setSpanAttributes(
          span: Span,
          attributes: Attributes
      ): Task[Unit] =
        ZIO.succeed(span.setAllAttributes(attributes)).unit

    }
}

object ZIOpenTelemetryTracing {

  /** Create a new ZIOpenTelemetryTracing interceptor with the provided Tracing and default configuration.
    *
    * @param tracing
    * @return
    */
  def apply(
      tracing: Tracing
  ): ZIOpenTelemetryTracing =
    new ZIOpenTelemetryTracing(
      tracing,
      ZIOpenTelemetryTracingConfig()
    )

  /** Create a new ZIOpenTelemetryTracing interceptor with the provided Tracing and configuration.
    */
  def apply(
      tracing: Tracing,
      config: ZIOpenTelemetryTracingConfig
  ): ZIOpenTelemetryTracing =
    new ZIOpenTelemetryTracing(
      tracing,
      config
    )

}
