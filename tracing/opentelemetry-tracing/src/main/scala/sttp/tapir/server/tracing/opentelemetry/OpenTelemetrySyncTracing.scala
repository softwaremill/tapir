package sttp.tapir.server.tracing.opentelemetry

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Tracer
import sttp.monad.MonadError
import sttp.shared.Identity
import sttp.tapir.AnyEndpoint
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.RequestResult.{Failure, Response}
import sttp.tapir.server.interceptor.{
  DecodeFailureContext,
  DecodeSuccessContext,
  EndpointHandler,
  EndpointInterceptor,
  RequestHandler,
  RequestInterceptor,
  RequestResult,
  Responder,
  SecurityFailureContext
}
import sttp.tapir.server.interpreter.BodyListener
import sttp.tapir.server.model.ServerResponse

/** Interceptor which traces requests using OpenTelemetry.
  *
  * Span names and attributes are calculated using the provided [[OpenTelemetryTracingSyncConfig]].
  *
  * To use, customize the interceptors of the server interpreter you are using, and prepend this interceptor, so that it runs as early as
  * possible, e.g.:
  *
  * {{{
  * val otel: OpenTelemetry = ???
  * val serverOptions: NettySyncServerOptions =
  *    NettySyncServerOptions.customiseInterceptors
  *      .prependInterceptor(OpenTelemetrySyncTracing(otel))
  *      .options
  * }}}
  *
  * Relies on the built-in OpenTelemetry Java SDK [[io.opentelemetry.context.ContextStorage]] mechanism of propagating the tracing context;
  * by default, this is using [[ThreadLocal]]s, and is hence only useable in synchronous/direct-style environments. That's why the effect
  * type is fixed to be [[Identity]].
  *
  * @param config
  */
class OpenTelemetrySyncTracing(config: OpenTelemetryTracingSyncConfig) extends RequestInterceptor[Identity] {

  override def apply[R, B](
      responder: Responder[Identity, B],
      requestHandler: EndpointInterceptor[Identity] => RequestHandler[Identity, R, B]
  ): RequestHandler[Identity, R, B] = new RequestHandler[Identity, R, B] {
    override def apply(request: ServerRequest, endpoints: List[ServerEndpoint[R, Identity]])(implicit
        monad: MonadError[Identity]
    ): RequestResult[B] = {

      val span = config.tracer
        .spanBuilder(config.spanName(request))
        .setAllAttributes(config.requestAttributes(request))
        .startSpan()

      val knownEndpointInterceptor = new EndpointInterceptor[Identity] {
        def apply[B](responder: Responder[Identity, B], endpointHandler: EndpointHandler[Identity, B]): EndpointHandler[Identity, B] = {
          new EndpointHandler[Identity, B] {
            def onDecodeFailure(
                ctx: DecodeFailureContext
            )(implicit monad: MonadError[Identity], bodyListener: BodyListener[Identity, B]): Option[ServerResponse[B]] = {
              val result = endpointHandler.onDecodeFailure(ctx)
              if (result.isDefined) {
                // only setting the attributes if a response has been created using this endpoint
                knownEndpoint(ctx.endpoint)
              }
              result
            }

            def onDecodeSuccess[A, U, I](
                ctx: DecodeSuccessContext[Identity, A, U, I]
            )(implicit monad: MonadError[Identity], bodyListener: BodyListener[Identity, B]): ServerResponse[B] = {
              knownEndpoint(ctx.endpoint)
              endpointHandler.onDecodeSuccess(ctx)
            }

            def onSecurityFailure[A](
                ctx: SecurityFailureContext[Identity, A]
            )(implicit monad: MonadError[Identity], bodyListener: BodyListener[Identity, B]): ServerResponse[B] = {
              knownEndpoint(ctx.endpoint)
              endpointHandler.onSecurityFailure(ctx)
            }

            def knownEndpoint(e: AnyEndpoint): Unit = {
              val (name, attributes) = config.spanNameFromEndpointAndAttributes(request, e)
              span.updateName(name)
              val _ = span.setAllAttributes(attributes)
            }
          }
        }
      }

      try {
        val scope = span.makeCurrent()
        try {
          try {
            val result = requestHandler(knownEndpointInterceptor)(request, endpoints)

            result match {
              case Response(response) =>
                span.setAllAttributes(config.responseAttributes(request, response))
                // https://opentelemetry.io/docs/specs/semconv/http/http-spans/#status
                if (response.isServerError) {
                  span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR)
                  val _ = span.setAllAttributes(config.errorAttributes(Left(response.code)))
                }
              case Failure(_) => span.setAllAttributes(config.noEndpointsMatchAttributes)
            }

            result
          } catch {
            case e: Exception =>
              span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR)
              span.setAllAttributes(config.errorAttributes(Right(e)))
              throw e
          }
        } finally {
          scope.close()
        }
      } finally {
        span.end()
      }
    }
  }
}

object OpenTelemetrySyncTracing {

  /** Create the tracing interceptor using the provided configuration. */
  def apply(config: OpenTelemetryTracingSyncConfig): OpenTelemetrySyncTracing = new OpenTelemetrySyncTracing(config)

  /** Create the tracing interceptor using the default configuration, created using the given [[OpenTelemetry]] instance. */
  def apply(openTelemetry: OpenTelemetry): OpenTelemetrySyncTracing = apply(OpenTelemetryTracingSyncConfig(openTelemetry))

  /** Create the tracing interceptor using the default configuration, created using the given [[Tracer]] instance. */
  def apply(tracer: Tracer): OpenTelemetrySyncTracing = apply(OpenTelemetryTracingSyncConfig.usingTracer(tracer))
}
