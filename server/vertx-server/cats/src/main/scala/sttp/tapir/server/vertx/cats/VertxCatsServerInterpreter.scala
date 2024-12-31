package sttp.tapir.server.vertx.cats

import cats.effect.std.Dispatcher
import cats.effect.{Async, Sync}
import cats.syntax.all._
import io.vertx.core.{Future, Handler}
import io.vertx.ext.web.{Route, Router, RoutingContext}
import sttp.capabilities.{Streams, WebSockets}
import sttp.capabilities.fs2.Fs2Streams
import sttp.monad.MonadError
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.RequestResult
import sttp.tapir.server.interpreter.{BodyListener, ServerInterpreter}
import sttp.tapir.server.vertx.{VertxBodyListener, VertxErrorHandler}
import sttp.tapir.server.vertx.cats.VertxCatsServerInterpreter.{CatsFFromVFuture, CatsRunAsync, VertxFutureToCatsF, monadError}
import sttp.tapir.server.vertx.decoders.{VertxRequestBody, VertxServerRequest}
import sttp.tapir.server.vertx.encoders.{VertxOutputEncoders, VertxToResponseBody}
import sttp.tapir.server.vertx.interpreters.{CommonServerInterpreter, FromVFuture, RunAsync}
import sttp.tapir.server.vertx.routing.PathMapping.extractRouteDefinition
import sttp.tapir.server.vertx.streams.ReadStreamCompatible
import sttp.tapir.server.vertx.cats.streams.fs2.fs2ReadStreamCompatible

import java.util.concurrent.atomic.AtomicReference

trait VertxCatsServerInterpreter[F[_]] extends CommonServerInterpreter with VertxErrorHandler {

  implicit def fa: Async[F]

  def vertxCatsServerOptions: VertxCatsServerOptions[F]

  /** Given a Router, creates and mounts a Route matching this endpoint, with default error handling
    * @return
    *   A function, that given a router, will attach this endpoint to it
    */
  def route(
      e: ServerEndpoint[Fs2Streams[F] with WebSockets, F]
  ): Router => Route = { router =>
    val routeDef = extractRouteDefinition(e.endpoint)
    val readStreamCompatible = fs2ReadStreamCompatible(vertxCatsServerOptions)
    optionsRoute(e)(router, routeDef).foreach(_.handler(endpointHandler(e, readStreamCompatible)))
    mountWithDefaultHandlers(e)(router, routeDef, vertxCatsServerOptions)
      .handler(endpointHandler(e, readStreamCompatible))
  }

  private def endpointHandler[S <: Streams[S]](
      e: ServerEndpoint[Fs2Streams[F] with WebSockets, F],
      readStreamCompatible: ReadStreamCompatible[S]
  ): Handler[RoutingContext] = {
    implicit val monad: MonadError[F] = monadError[F]
    implicit val bodyListener: BodyListener[F, RoutingContext => Future[Void]] =
      new VertxBodyListener[F](new CatsRunAsync(vertxCatsServerOptions.dispatcher))
    val fFromVFuture = new CatsFFromVFuture[F]
    val interpreter: ServerInterpreter[Fs2Streams[F] with WebSockets, F, RoutingContext => Future[Void], S] = new ServerInterpreter(
      _ => List(e),
      new VertxRequestBody(vertxCatsServerOptions, fFromVFuture)(readStreamCompatible),
      new VertxToResponseBody(vertxCatsServerOptions)(readStreamCompatible),
      vertxCatsServerOptions.interceptors,
      vertxCatsServerOptions.deleteFile
    )

    new Handler[RoutingContext] {
      override def handle(rc: RoutingContext) = {
        val serverRequest = VertxServerRequest(rc)

        val result = interpreter(serverRequest)
          .flatMap {
            // in vertx, endpoints are attempted to be decoded individually; if this endpoint didn't match - another one might
            case RequestResult.Failure(_)         => Async[F].delay(rc.next())
            case RequestResult.Response(response) => fFromVFuture(VertxOutputEncoders(response).apply(rc)).void
          }
          .handleErrorWith(handleError(rc, _).asF.void)

        // we obtain the cancel token only after the effect is run, so we need to pass it to the exception handler
        // via a mutable ref; however, before this is done, it's possible an exception has already been reported;
        // if so, we need to use this fact to cancel the operation nonetheless
        type CancelToken = () => scala.concurrent.Future[Unit]
        val cancelRef = new AtomicReference[Option[Either[Throwable, CancelToken]]](None)

        rc.response.exceptionHandler { (t: Throwable) =>
          cancelRef.getAndSet(Some(Left(t))).collect { case Right(t) =>
            t()
          }
          ()
        }

        val cancelToken = vertxCatsServerOptions.dispatcher.unsafeRunCancelable(result)
        cancelRef.getAndSet(Some(Right(cancelToken))).collect { case Left(_) =>
          cancelToken()
        }

        ()
      }
    }
  }
}

object VertxCatsServerInterpreter {
  def apply[F[_]](dispatcher: Dispatcher[F])(implicit _fa: Async[F]): VertxCatsServerInterpreter[F] = {
    new VertxCatsServerInterpreter[F] {
      override implicit def fa: Async[F] = _fa
      override def vertxCatsServerOptions: VertxCatsServerOptions[F] = VertxCatsServerOptions.default[F](dispatcher)(fa)
    }
  }

  def apply[F[_]](serverOptions: VertxCatsServerOptions[F])(implicit _fa: Async[F]): VertxCatsServerInterpreter[F] = {
    new VertxCatsServerInterpreter[F] {
      override implicit def fa: Async[F] = _fa
      override def vertxCatsServerOptions: VertxCatsServerOptions[F] = serverOptions
    }
  }

  private[cats] def monadError[F[_]](implicit F: Sync[F]): MonadError[F] = new MonadError[F] {
    override def unit[T](t: T): F[T] = F.pure(t)
    override def map[T, T2](fa: F[T])(f: T => T2): F[T2] = F.map(fa)(f)
    override def flatMap[T, T2](fa: F[T])(f: T => F[T2]): F[T2] = F.flatMap(fa)(f)
    override def error[T](t: Throwable): F[T] = F.raiseError(t)
    override protected def handleWrappedError[T](rt: F[T])(h: PartialFunction[Throwable, F[T]]): F[T] = F.recoverWith(rt)(h)
    override def eval[T](t: => T): F[T] = F.delay(t)
    override def suspend[T](t: => F[T]): F[T] = F.defer(t)
    override def flatten[T](ffa: F[F[T]]): F[T] = F.flatten(ffa)
    override def ensure[T](f: F[T], e: => F[Unit]): F[T] = F.guaranteeCase(f)(_ => e)
    override def blocking[T](t: => T): F[T] = F.blocking(t)
  }

  private[cats] class CatsFFromVFuture[F[_]: Async] extends FromVFuture[F] {
    def apply[T](f: => Future[T]): F[T] = f.asF
  }

  private[cats] class CatsRunAsync[F[_]: Async](dispatcher: Dispatcher[F]) extends RunAsync[F] {
    override def apply[T](f: => F[T]): Unit = dispatcher.unsafeRunAndForget(f)
  }

  implicit class VertxFutureToCatsF[A](f: => Future[A]) {
    def asF[F[_]: Async]: F[A] = {
      Async[F].async { cb =>
        Sync[F].delay {
          f.onComplete({ handler =>
            if (handler.succeeded()) {
              cb(Right(handler.result()))
            } else {
              cb(Left(handler.cause()))
            }
          })
          Some(().pure[F])
        }
      }
    }
  }
}
