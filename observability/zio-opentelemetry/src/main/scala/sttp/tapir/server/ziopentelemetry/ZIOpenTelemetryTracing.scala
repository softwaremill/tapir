package sttp.tapir.server.ziopentelemetry

import zio._

import collection.mutable.{Map => MutableMap}
import scala.util.{Failure, Success, Try}

import sttp.monad.MonadError
import sttp.model.{StatusCode => SttpStatusCode}
import sttp.tapir.AnyEndpoint
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor._
import sttp.tapir.server.interpreter.BodyListener
import sttp.tapir.server.interpreter.BodyListener._
import sttp.tapir.server.model.ServerResponse

import io.opentelemetry.api.trace.{Span, SpanKind, StatusCode}
import io.opentelemetry.api.common.Attributes

import zio.telemetry.opentelemetry.tracing.Tracing
import zio.telemetry.opentelemetry.context.IncomingContextCarrier

/** Interceptor which traces requests using ZIO OpenTelemetry.
  *
  * Span names and attributes are calculated using the provided [[ZIOpenTelemetryTracingConfig]].
  *
  * The span is finalized once the response body has been fully sent, which is wired up through the [[BodyListener]] available in the
  * endpoint interceptor. This way streamed responses are correctly captured, including their duration and any errors which occur while the
  * body is being produced. The interceptor is backend-agnostic: it does not depend on any particular server backend's body representation.
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

  private def extractCarrier(request: ServerRequest) = {
    val carrier = MutableMap.empty[String, String]
    request.headers.foreach(h => carrier.put(h.name, h.value))
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
      )(implicit monad: MonadError[Task]): Task[RequestResult[B]] =
        tracing
          .extractSpanUnsafe(
            config.propagator,
            extractCarrier(request),
            request.showShort,
            spanKind = SpanKind.SERVER,
            attributes = config.requestAttributes(request)
          )
          .flatMap { case (span, finalize) =>
            requestHandler(knownEndpointInterceptor(request, span, finalize))(request, endpoints)
              .flatMap {
                // The response was produced by an endpoint: the span is finalized once the response body has been fully
                // sent, which is wired up via the BodyListener in knownEndpointInterceptor.
                case r @ RequestResult.Response(_, ResponseSource.EndpointHandler) =>
                  monad.unit(r)
                // The response was produced at the request level (e.g. by another interceptor); no body listener is
                // available here, so the span is finalized immediately.
                case RequestResult.Response(response, source) =>
                  (setSpanAttributes(span, config.responseAttributes(request, response)) *>
                    ZIO.when(response.isServerError)(spanError(span)(Left(response.code))))
                    .ensuring(finalize)
                    .as(RequestResult.Response(response, source))
                // A Failure means no endpoint produced a response (unmatched / decode / auth failure). Per the OTel
                // HTTP server semantic conventions a client error (4xx) is not a server-span error, so the span is not
                // marked as errored here - it's only finalized.
                case f @ RequestResult.Failure(_) =>
                  finalize.as(f)
              }
              .tapError(e => spanError(span)(Right(e)).ensuring(finalize))
          }
    }

  /** Endpoint interceptor which, once an endpoint is matched, updates the span name/attributes, and arranges for the span to be finalized
    * after the response body has been fully sent.
    */
  private def knownEndpointInterceptor(
      request: ServerRequest,
      span: Span,
      finalizeSpan: UIO[Any]
  ): EndpointInterceptor[Task] =
    new EndpointInterceptor[Task] {
      override def apply[B2](
          responder: Responder[Task, B2],
          endpointHandler: EndpointHandler[Task, B2]
      ): EndpointHandler[Task, B2] = new EndpointHandler[Task, B2] {

        override def onDecodeSuccess[A, U, I](
            ctx: DecodeSuccessContext[Task, A, U, I]
        )(implicit monad: MonadError[Task], bodyListener: BodyListener[Task, B2]): Task[ServerResponse[B2]] =
          knownEndpoint(ctx.endpoint) *> endpointHandler.onDecodeSuccess(ctx).flatMap(finalizeOnBodyComplete)

        override def onSecurityFailure[A](
            ctx: SecurityFailureContext[Task, A]
        )(implicit monad: MonadError[Task], bodyListener: BodyListener[Task, B2]): Task[ServerResponse[B2]] =
          knownEndpoint(ctx.endpoint) *> endpointHandler.onSecurityFailure(ctx).flatMap(finalizeOnBodyComplete)

        override def onDecodeFailure(
            ctx: DecodeFailureContext
        )(implicit monad: MonadError[Task], bodyListener: BodyListener[Task, B2]): Task[Option[ServerResponse[B2]]] =
          endpointHandler.onDecodeFailure(ctx).flatMap {
            case Some(response) => knownEndpoint(ctx.endpoint) *> finalizeOnBodyComplete(response).map(Some(_))
            case None           => monad.unit(None)
          }

        /** Sets the response attributes, then finalizes the span once the response body has been fully sent (or immediately, if there's no
          * body). If sending the body fails, the span is additionally marked as errored.
          */
        private def finalizeOnBodyComplete(
            response: ServerResponse[B2]
        )(implicit bodyListener: BodyListener[Task, B2]): Task[ServerResponse[B2]] = {
          val cb: Try[Unit] => Task[Unit] = {
            case Success(_)  => ZIO.when(response.isServerError)(spanError(span)(Left(response.code))).unit.ensuring(finalizeSpan)
            case Failure(ex) => spanError(span)(Right(ex)).ensuring(finalizeSpan)
          }
          setSpanAttributes(span, config.responseAttributes(request, response)) *> (response.body match {
            case Some(body) => body.onComplete(cb).map(b => response.copy(body = Some(b)))
            case None       => cb(Success(())).as(response)
          })
        }

        private def knownEndpoint(e: AnyEndpoint): Task[Unit] = {
          val (name, attributes) = config.spanNameFromEndpointAndAttributes(request, e)
          ZIO.attempt {
            span.updateName(name)
            span.setAllAttributes(attributes)
            ()
          }
        }
      }
    }

  /** Set span status and attributes for errors, both exceptions and error status. */
  private def spanError(span: Span)(error: Either[SttpStatusCode, Throwable]): Task[Unit] =
    ZIO.attempt {
      span.setStatus(StatusCode.ERROR)
      span.setAllAttributes(config.errorAttributes(error))
      ()
    }

  private def setSpanAttributes(span: Span, attributes: Attributes): Task[Unit] =
    ZIO.attempt(span.setAllAttributes(attributes)).unit
}

object ZIOpenTelemetryTracing {

  /** Create a new ZIOpenTelemetryTracing interceptor with the provided Tracing and default configuration. */
  def apply(tracing: Tracing): ZIOpenTelemetryTracing =
    new ZIOpenTelemetryTracing(tracing, ZIOpenTelemetryTracingConfig())

  /** Create a new ZIOpenTelemetryTracing interceptor with the provided Tracing and configuration. */
  def apply(tracing: Tracing, config: ZIOpenTelemetryTracingConfig): ZIOpenTelemetryTracing =
    new ZIOpenTelemetryTracing(tracing, config)
}
