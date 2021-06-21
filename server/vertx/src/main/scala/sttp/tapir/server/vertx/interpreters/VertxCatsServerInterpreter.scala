package sttp.tapir.server.vertx.interpreters

import cats.effect.{Async, ConcurrentEffect, Effect, Sync}
import cats.syntax.all._
import io.vertx.core.{Future, Handler}
import io.vertx.ext.web.{Route, Router, RoutingContext}
import sttp.capabilities.fs2.Fs2Streams
import sttp.monad.MonadError
import sttp.tapir.Endpoint
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interpreter.{BodyListener, ServerInterpreter}
import sttp.tapir.server.vertx.decoders.{VertxRequestBody, VertxServerRequest}
import sttp.tapir.server.vertx.encoders.{VertxOutputEncoders, VertxToResponseBody}
import sttp.tapir.server.vertx.interpreters.VertxCatsServerInterpreter.{CatsFFromVFuture, monadError}
import sttp.tapir.server.vertx.routing.PathMapping.extractRouteDefinition
import sttp.tapir.server.vertx.streams.ReadStreamCompatible
import sttp.tapir.server.vertx.{VertxBodyListener, VertxCatsServerOptions}

import scala.reflect.ClassTag

trait VertxCatsServerInterpreter[F[_]] extends CommonServerInterpreter {

  implicit def fs: Sync[F]

  def vertxCatsServerOptions: VertxCatsServerOptions[F] = VertxCatsServerOptions.default[F]

  /** Given a Router, creates and mounts a Route matching this endpoint, with default error handling
    * @param logic the logic to associate with the endpoint
    * @return A function, that given a router, will attach this endpoint to it
    */
  def route[I, E, O](e: Endpoint[I, E, O, Fs2Streams[F]])(logic: I => F[Either[E, O]])(implicit
      effect: ConcurrentEffect[F]
  ): Router => Route =
    route(e.serverLogic(logic))

  /** Given a Router, creates and mounts a Route matching this endpoint, with custom error handling
    * @param logic the logic to associate with the endpoint
    * @return A function, that given a router, will attach this endpoint to it
    */
  def routeRecoverErrors[I, E, O](e: Endpoint[I, E, O, Fs2Streams[F]])(
      logic: I => F[O]
  )(implicit
      effect: ConcurrentEffect[F],
      eIsThrowable: E <:< Throwable,
      eClassTag: ClassTag[E]
  ): Router => Route =
    route(e.serverLogicRecoverErrors(logic))

  /** Given a Router, creates and mounts a Route matching this endpoint, with default error handling
    * @return A function, that given a router, will attach this endpoint to it
    */
  def route[I, E, O](
      e: ServerEndpoint[I, E, O, Fs2Streams[F], F]
  )(implicit
      effect: ConcurrentEffect[F]
  ): Router => Route = { router =>
    import sttp.tapir.server.vertx.streams.fs2._
    mountWithDefaultHandlers(e)(router, extractRouteDefinition(e.endpoint)).handler(endpointHandler(e))
  }

  private def endpointHandler[I, E, O, A, S: ReadStreamCompatible](
      e: ServerEndpoint[I, E, O, _, F]
  )(implicit effect: Effect[F]): Handler[RoutingContext] = { rc =>
    implicit val monad: MonadError[F] = monadError[F]
    implicit val bodyListener: BodyListener[F, RoutingContext => Unit] = new VertxBodyListener[F]
    val fFromVFuture = new CatsFFromVFuture[F]
    val interpreter: ServerInterpreter[Nothing, F, RoutingContext => Unit, S] = new ServerInterpreter(
      new VertxRequestBody(rc, vertxCatsServerOptions, fFromVFuture),
      new VertxToResponseBody(vertxCatsServerOptions),
      vertxCatsServerOptions.interceptors,
      vertxCatsServerOptions.deleteFile
    )
    val serverRequest = new VertxServerRequest(rc)

    val result = interpreter(serverRequest, e)
      .flatMap {
        case None           => fFromVFuture(rc.response.setStatusCode(404).end()).void
        case Some(response) => VertxOutputEncoders(response).apply(rc).pure
      }
      .handleError { e => rc.fail(e) }

    val cancelToken = effect.toIO(result).unsafeRunCancelable { _ => () }
    rc.response.exceptionHandler { _ =>
      cancelToken.unsafeRunSync()
    }
    ()
  }
}

object VertxCatsServerInterpreter {
  def apply[F[_]]()(implicit _fs: Sync[F]): VertxCatsServerInterpreter[F] = {
    new VertxCatsServerInterpreter[F] {
      override implicit def fs: Sync[F] = _fs
    }
  }

  def apply[F[_]](serverOptions: VertxCatsServerOptions[F])(implicit _fs: Sync[F]): VertxCatsServerInterpreter[F] = {
    new VertxCatsServerInterpreter[F] {
      override implicit def fs: Sync[F] = _fs

      override def vertxCatsServerOptions: VertxCatsServerOptions[F] = serverOptions
    }
  }

  private[vertx] def monadError[F[_]](implicit F: Effect[F]): MonadError[F] = new MonadError[F] {
    override def unit[T](t: T): F[T] = F.pure(t)
    override def map[T, T2](fa: F[T])(f: T => T2): F[T2] = F.map(fa)(f)
    override def flatMap[T, T2](fa: F[T])(f: T => F[T2]): F[T2] = F.flatMap(fa)(f)
    override def error[T](t: Throwable): F[T] = F.raiseError(t)
    override protected def handleWrappedError[T](rt: F[T])(h: PartialFunction[Throwable, F[T]]): F[T] =
      F.recoverWith(rt)(h)
    override def eval[T](t: => T): F[T] = F.delay(t)
    override def suspend[T](t: => F[T]): F[T] = F.defer(t)
    override def flatten[T](ffa: F[F[T]]): F[T] = F.flatten(ffa)
    override def ensure[T](f: F[T], e: => F[Unit]): F[T] = F.guarantee(f)(e)
  }

  private[vertx] class CatsFFromVFuture[F[_]: Async] extends FromVFuture[F] {
    def apply[T](f: => Future[T]): F[T] = f.asF
  }

  implicit class VertxFutureToCatsF[A](f: => Future[A]) {
    def asF[F[_]: Async]: F[A] = {
      Async[F].async { cb =>
        f.onComplete({ handler =>
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
}