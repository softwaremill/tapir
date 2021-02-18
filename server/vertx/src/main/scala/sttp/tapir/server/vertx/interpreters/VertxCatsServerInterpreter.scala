package sttp.tapir.server.vertx.interpreters

import cats.effect.{Async, ConcurrentEffect, Effect}
import io.vertx.core.{Future, Handler}
import io.vertx.ext.web.{Route, Router, RoutingContext}
import sttp.capabilities.fs2.Fs2Streams
import sttp.monad.MonadError
import sttp.tapir.server.vertx.handlers.tryEncodeError
import sttp.tapir.server.vertx.decoders.VertxInputDecoders.decodeBodyAndInputsThen
import sttp.tapir.server.vertx.routing.PathMapping.extractRouteDefinition
import sttp.tapir.server.vertx.streams.ReadStreamCompatible
import sttp.tapir.server.vertx.VertxEffectfulEndpointOptions
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.internal.Params
import sttp.tapir.Endpoint

import scala.reflect.ClassTag

trait VertxCatsServerInterpreter extends CommonServerInterpreter {

  /** Given a Router, creates and mounts a Route matching this endpoint, with default error handling
    * @param logic the logic to associate with the endpoint
    * @param endpointOptions options associated to the endpoint, like its logging capabilities, or execution context
    * @return A function, that given a router, will attach this endpoint to it
    */
  def route[F[_], I, E, O](e: Endpoint[I, E, O, Fs2Streams[F]])(logic: I => F[Either[E, O]])(implicit
      endpointOptions: VertxEffectfulEndpointOptions,
      effect: ConcurrentEffect[F]
  ): Router => Route =
    route(e.serverLogic(logic))

  /** Given a Router, creates and mounts a Route matching this endpoint, with custom error handling
    * @param logic the logic to associate with the endpoint
    * @param endpointOptions options associated to the endpoint, like its logging capabilities, or execution context
    * @return A function, that given a router, will attach this endpoint to it
    */
  def routeRecoverErrors[F[_], I, E, O](e: Endpoint[I, E, O, Fs2Streams[F]])(
      logic: I => F[O]
  )(implicit
      endpointOptions: VertxEffectfulEndpointOptions,
      effect: ConcurrentEffect[F],
      eIsThrowable: E <:< Throwable,
      eClassTag: ClassTag[E]
  ): Router => Route =
    route(e.serverLogicRecoverErrors(logic))

  /** Given a Router, creates and mounts a Route matching this endpoint, with default error handling
    * @param endpointOptions options associated to the endpoint, like its logging capabilities, or execution context
    * @return A function, that given a router, will attach this endpoint to it
    */
  def route[F[_], I, E, O](
      e: ServerEndpoint[I, E, O, Fs2Streams[F], F]
  )(implicit
      endpointOptions: VertxEffectfulEndpointOptions,
      effect: ConcurrentEffect[F]
  ): Router => Route = { router =>
    implicit val ect: Option[ClassTag[E]] = None
    import sttp.tapir.server.vertx.streams.fs2._
    mountWithDefaultHandlers(e)(router, extractRouteDefinition(e.endpoint))
      .handler(endpointHandler(e)(e.logic, responseHandlerWithError(e)))
  }

  private def endpointHandler[F[_], I, E, O, A, S: ReadStreamCompatible](e: ServerEndpoint[I, E, O, _, F])(
      logic: MonadError[F] => I => F[A],
      responseHandler: (A, RoutingContext) => Unit
  )(implicit
      serverOptions: VertxEffectfulEndpointOptions,
      effect: Effect[F],
      ect: Option[ClassTag[E]]
  ): Handler[RoutingContext] = { rc =>
    decodeBodyAndInputsThen(
      e.endpoint,
      rc,
      logicHandler(e)(logic(monadError[F]), responseHandler, rc)
    )
  }

  def monadError[F[_]](implicit F: Effect[F]): MonadError[F] = new MonadError[F] {
    override def unit[T](t: T): F[T] = F.pure(t)
    override def map[T, T2](fa: F[T])(f: T => T2): F[T2] = F.map(fa)(f)
    override def flatMap[T, T2](fa: F[T])(f: T => F[T2]): F[T2] = F.flatMap(fa)(f)
    override def error[T](t: Throwable): F[T] = F.raiseError(t)
    override protected def handleWrappedError[T](rt: F[T])(h: PartialFunction[Throwable, F[T]]): F[T] =
      F.recoverWith(rt)(h)
    override def eval[T](t: => T): F[T] = F.delay(t)
    override def suspend[T](t: => F[T]): F[T] = F.suspend(t)
    override def flatten[T](ffa: F[F[T]]): F[T] = F.flatten(ffa)
    override def ensure[T](f: F[T], e: => F[Unit]): F[T] = F.guarantee(f)(e)
  }

  private def logicHandler[F[_], I, E, O, T, S: ReadStreamCompatible](
      e: ServerEndpoint[I, E, O, _, F]
  )(logic: I => F[T], responseHandler: (T, RoutingContext) => Unit, rc: RoutingContext)(implicit
      effect: Effect[F],
      serverOptions: VertxEffectfulEndpointOptions,
      ect: Option[ClassTag[E]]
  ): Params => Unit = { params =>
    val cancelToken = effect.toIO(logic(params.asAny.asInstanceOf[I])).unsafeRunCancelable {
      case Right(result) =>
        responseHandler(result, rc)
      case Left(cause) =>
        serverOptions.logRequestHandling.logicException(e.endpoint, cause)(serverOptions.logger)
        tryEncodeError[E, S](e.endpoint, rc, cause)
    }

    rc.response.exceptionHandler { _ =>
      cancelToken.unsafeRunSync()
    }

    ()
  }

  implicit class VertxFutureForCats[A](future: => Future[A]) {

    def liftF[F[_]: Async]: F[A] =
      Async[F].async { cb =>
        future.onComplete({ handler =>
          if (handler.succeeded()) {
            cb(Right(handler.result()))
          } else {
            cb(Left(handler.cause()))
          }
        })
        ()
      }
  }
}
