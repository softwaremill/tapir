package sttp.tapir.server.tracing.otel4s

import org.typelevel.otel4s.context.propagation.TextMapGetter
import org.typelevel.otel4s.trace
import org.typelevel.otel4s.trace.{Span, Tracer}
import sttp.monad.MonadError
import sttp.monad.syntax.MonadErrorOps
import sttp.tapir.AnyEndpoint
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.RequestResult.{Failure, Response}
import sttp.tapir.server.interceptor._
import sttp.tapir.server.interpreter.BodyListener
import sttp.tapir.server.model.ServerResponse

/** Interceptor which traces requests using otel4s.
  *
  * Span names and attributes are calculated using the provided [[Otel4sTracingConfig]].
  *
  * To use, customize the interceptors of the server interpreter you are using, and prepend this interceptor, so that it runs as early as
  * possible, e.g.:
  *
  * {{{
  * OtelJava
  *   .autoConfigured[IO]()
  *   .use { otel4s =>
  *     otel4s.tracerProvider.get("tracer name").flatMap { tracer =>
  *       def endpoints: List[ServerEndpoint[Any, IO]] = ???
  *       val routes =
  *         Http4sServerInterpreter[IO](Http4sServerOptions.default[IO].prependInterceptor(Otel4sTracing(tracing)))
  *           .toRoutes(endpoints)
  *        //...
  *   }
  * }
  * }}}
  * See https://typelevel.org/otel4s/oteljava/tracing-context-propagation.html for details on context propagation.
  */
class Otel4sTracing[F[_]](config: Otel4sTracingConfig[F]) extends RequestInterceptor[F] {
  import config._

  // https://typelevel.org/otel4s/instrumentation/tracing-cross-service-propagation.html
  implicit private val getter: TextMapGetter[ServerRequest] = new TextMapGetter[ServerRequest] {
    override def get(carrier: ServerRequest, key: String): Option[String] = carrier.header(key)
    override def keys(carrier: ServerRequest): Iterable[String] = carrier.headers.map(_.name)
  }

  override def apply[R, B](
      responder: Responder[F, B],
      requestHandler: EndpointInterceptor[F] => RequestHandler[F, R, B]
  ): RequestHandler[F, R, B] = new RequestHandler[F, R, B] {
    override def apply(request: ServerRequest, endpoints: List[ServerEndpoint[R, F]])(implicit
        monad: MonadError[F]
    ): F[RequestResult[B]] = {
      tracer.joinOrRoot(request)(
        tracer
          .span(spanName(request), requestAttributes(request))
          .use(span =>
            (for {
              requestResult <- requestHandler(knownEndpointInterceptor(request, span))(request, endpoints)
              _ <- requestResult match {
                case Response(response) =>
                  span
                    .addAttributes(responseAttributes(request, response))
                    .flatMap(_ =>
                      if (response.isServerError)
                        span.setStatus(trace.StatusCode.Error).flatMap(_ => span.addAttributes(errorAttributes(Left(response.code))))
                      else monad.unit(())
                    )
                case Failure(_) =>
                  // ignore, request not handled
                  monad.unit(())
              }
            } yield requestResult)
              .handleError { case e: Exception =>
                span
                  .setStatus(trace.StatusCode.Error)
                  .flatMap(_ => span.addAttributes(errorAttributes(Right(e))))
                  .flatMap(_ => monad.error(e))
              }
          )
      )
    }
  }

  private def knownEndpointInterceptor(request: ServerRequest, span: Span[F]) = new EndpointInterceptor[F] {
    def apply[B](responder: Responder[F, B], endpointHandler: EndpointHandler[F, B]): EndpointHandler[F, B] = new EndpointHandler[F, B] {
      def onDecodeFailure(
          ctx: DecodeFailureContext
      )(implicit monad: MonadError[F], bodyListener: BodyListener[F, B]): F[Option[ServerResponse[B]]] =
        endpointHandler.onDecodeFailure(ctx).flatMap {
          case result @ Some(_) => knownEndpoint(ctx.endpoint).map(_ => result)
          case None             => monad.unit(None)
        }

      def onDecodeSuccess[A, U, I](
          ctx: DecodeSuccessContext[F, A, U, I]
      )(implicit monad: MonadError[F], bodyListener: BodyListener[F, B]): F[ServerResponse[B]] =
        knownEndpoint(ctx.endpoint).flatMap(_ => endpointHandler.onDecodeSuccess(ctx))

      def onSecurityFailure[A](
          ctx: SecurityFailureContext[F, A]
      )(implicit monad: MonadError[F], bodyListener: BodyListener[F, B]): F[ServerResponse[B]] =
        knownEndpoint(ctx.endpoint).flatMap(_ => endpointHandler.onSecurityFailure(ctx))

      def knownEndpoint(e: AnyEndpoint)(implicit monad: MonadError[F]): F[Unit] = {
        val (name, attributes) = spanNameFromEndpointAndAttributes(request, e)
        span.updateName(name).flatMap(_ => span.addAttributes(attributes))
      }
    }
  }
}

object Otel4sTracing {
  def apply[F[_]](config: Otel4sTracingConfig[F]): Otel4sTracing[F] = new Otel4sTracing(config)
  def apply[F[_]](tracer: Tracer[F]): Otel4sTracing[F] = new Otel4sTracing(Otel4sTracingConfig(tracer))
}
