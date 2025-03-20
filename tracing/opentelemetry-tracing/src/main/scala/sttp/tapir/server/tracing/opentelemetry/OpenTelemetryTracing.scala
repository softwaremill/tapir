package sttp.tapir.server.tracing.opentelemetry

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.{Span, Tracer}
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.{ContextPropagators, TextMapGetter}
import sttp.monad.MonadError
import sttp.monad.syntax._
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

import collection.JavaConverters._

/** Interceptor which traces requests using OpenTelemetry.
  *
  * Span names and attributes are calculated using the provided [[OpenTelemetryTracingConfig]].
  *
  * To use, customize the interceptors of the server interpreter you are using, and prepend this interceptor, so that it runs as early as
  * possible, e.g.:
  *
  * {{{
  * val otel: OpenTelemetry = ???
  * val serverOptions: NettySyncServerOptions =
  *    NettySyncServerOptions.customiseInterceptors
  *      .prependInterceptor(OpenTelemetryTracing(otel))
  *      .options
  * }}}
  *
  * Relies on the built-in OpenTelemetry Java SDK [[io.opentelemetry.context.ContextStorage]] mechanism of propagating the tracing context;
  * by default, this is using [[ThreadLocal]]s, which works with synchronous/direct-style environments. [[Future]]s are supported through
  * instrumentation provided by the OpenTelemetry javaagent. For functional effect systems, usually a dedicated integration library is
  * required.
  */
class OpenTelemetryTracing[F[_]](config: OpenTelemetryTracingConfig) extends RequestInterceptor[F] {

  private val getter = new TextMapGetter[ServerRequest] {
    override def get(carrier: ServerRequest, key: String): String = carrier.header(key).getOrElse(null)
    override def keys(carrier: ServerRequest): java.lang.Iterable[String] = carrier.headers.map(_.name).asJava
  }

  override def apply[R, B](
      responder: Responder[F, B],
      requestHandler: EndpointInterceptor[F] => RequestHandler[F, R, B]
  ): RequestHandler[F, R, B] = new RequestHandler[F, R, B] {
    override def apply(request: ServerRequest, endpoints: List[ServerEndpoint[R, F]])(implicit
        monad: MonadError[F]
    ): F[RequestResult[B]] = withPropagatedContext(request) {
      val span = config.tracer
        .spanBuilder(config.spanName(request))
        .setAllAttributes(config.requestAttributes(request))
        .startSpan()

      withSpan(span) {
        requestHandler(knownEndpointInterceptor(request, span))(request, endpoints)
          .map { result =>
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
          }
          .handleError { case e: Exception =>
            span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR)
            span.setAllAttributes(config.errorAttributes(Right(e)))
            monad.error(e)
          }
      }
    }
  }

  private def withPropagatedContext[T](request: ServerRequest)(f: => F[T]): F[T] = {
    val amendedContext = config.propagators.getTextMapPropagator().extract(Context.current(), request, getter)
    val scope = amendedContext.makeCurrent()
    try f
    finally scope.close()
  }

  private def withSpan[T](span: Span)(f: => F[T])(implicit monad: MonadError[F]): F[T] = {
    val scope = span.makeCurrent()
    monad.ensure2(
      {
        try f
        finally scope.close()
      },
      monad.eval(span.end())
    )
  }

  private def knownEndpointInterceptor(request: ServerRequest, span: Span) = new EndpointInterceptor[F] {
    def apply[B](responder: Responder[F, B], endpointHandler: EndpointHandler[F, B]): EndpointHandler[F, B] = {
      new EndpointHandler[F, B] {
        def onDecodeFailure(
            ctx: DecodeFailureContext
        )(implicit monad: MonadError[F], bodyListener: BodyListener[F, B]): F[Option[ServerResponse[B]]] = {
          endpointHandler.onDecodeFailure(ctx).map { result =>
            if (result.isDefined) {
              // only setting the attributes if a response has been created using this endpoint
              knownEndpoint(ctx.endpoint)
            }
            result
          }
        }

        def onDecodeSuccess[A, U, I](
            ctx: DecodeSuccessContext[F, A, U, I]
        )(implicit monad: MonadError[F], bodyListener: BodyListener[F, B]): F[ServerResponse[B]] = {
          monad.eval(knownEndpoint(ctx.endpoint)).flatMap(_ => endpointHandler.onDecodeSuccess(ctx))
        }

        def onSecurityFailure[A](
            ctx: SecurityFailureContext[F, A]
        )(implicit monad: MonadError[F], bodyListener: BodyListener[F, B]): F[ServerResponse[B]] = {
          monad.eval(knownEndpoint(ctx.endpoint)).flatMap(_ => endpointHandler.onSecurityFailure(ctx))
        }

        def knownEndpoint(e: AnyEndpoint): Unit = {
          val (name, attributes) = config.spanNameFromEndpointAndAttributes(request, e)
          span.updateName(name)
          val _ = span.setAllAttributes(attributes)
        }
      }
    }
  }
}

object OpenTelemetryTracing {

  /** Create the tracing interceptor using the provided configuration. */
  def apply[F[_]](config: OpenTelemetryTracingConfig): OpenTelemetryTracing[F] = new OpenTelemetryTracing[F](config)

  /** Create the tracing interceptor using the default configuration, created using the given [[OpenTelemetry]] instance. */
  def apply[F[_]](openTelemetry: OpenTelemetry): OpenTelemetryTracing[F] = apply(OpenTelemetryTracingConfig(openTelemetry))

  /** Create the tracing interceptor using the default configuration, created using the given [[Tracer]] instance. */
  def apply[F[_]](tracer: Tracer, propagators: ContextPropagators): OpenTelemetryTracing[F] = apply(
    OpenTelemetryTracingConfig.usingTracer(tracer, propagators)
  )
}
