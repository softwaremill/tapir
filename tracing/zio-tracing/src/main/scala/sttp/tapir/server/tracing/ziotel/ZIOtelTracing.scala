package sttp.tapir.server.tracing.ziotel

import zio._

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

/** Interceptor which traces requests using otel4s.
  *
  * Span names and attributes are calculated using the provided [[Otel4zTracingConfig]].
  *
  * To use, customize the interceptors of the server interpreter you are using, and prepend this interceptor, so that it runs as early as
  * possible, e.g.:
  *
  * {{{
  * protected def serverOptions(using
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

class ZIOtelTracing(
    tracing: Tracing,
    config: ZIOtelTracingConfig
) extends RequestInterceptor[Task] {

  import config._

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
          config.carrier,
          request.showShort,
          spanKind = SpanKind.SERVER,
          attributes = config.requestAttributes(request)
        )
        .flatMap { case (span, finalize) =>
          handleRequest(span, request, endpoints)
            .tapError(th => spanError(span)(Right(th)))
            .ensuring(finalize)
        }

      /** Handle the request, setting span attributes and status based on the result.
        *
        * @param span
        * @param request
        * @param endpoints
        * @param monad
        * @return
        */
      def handleRequest(
          span: Span,
          request: ServerRequest,
          endpoints: List[ServerEndpoint[R, Task]]
      )(implicit monad: MonadError[Task]) =
        for {
          requestResult <- requestHandler(
            knownEndpointInterceptor(request, span)
          )(request, endpoints)
          _ <- requestResult match {
            case Response(response, _) =>
              setSpanAttibutes(
                span,
                responseAttributes(request, response)
              ) *> ZIO.when(response.isServerError)(
                spanError(span)(Left(response.code))
              )
            case Failure(_) =>
              // ignore, request not handled
              ZIO.unit
          }
        } yield requestResult

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
          span: Span
      )(error: Either[SttpStatusCode, Throwable]): Task[Unit] =
        ZIO.succeed {
          span.setStatus(StatusCode.ERROR)
          span.setAllAttributes(errorAttributes(error))
        }.unit

      private def setSpanAttibutes(
          span: Span,
          attributes: Attributes
      ): Task[Unit] =
        ZIO.succeed(span.setAllAttributes(attributes)).unit

    }
}

object ZIOtelTracing {

  /** Create a new ZIOpenTelemetryTracing interceptor with the provided Tracing and default configuration.
    *
    * @param tracing
    * @return
    */
  def apply(
      tracing: Tracing
  ): ZIOtelTracing =
    new ZIOtelTracing(
      tracing,
      ZIOtelTracingConfig()
    )

  /** Create a new ZIOpenTelemetryTracing interceptor with the provided Tracing and configuration.
    */
  def apply(
      tracing: Tracing,
      config: ZIOtelTracingConfig
  ): ZIOtelTracing =
    new ZIOtelTracing(
      tracing,
      config
    )

}
