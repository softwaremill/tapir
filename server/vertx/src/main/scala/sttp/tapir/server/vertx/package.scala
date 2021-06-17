package sttp.tapir.server

import cats.effect.{Async, Effect, Sync}
import io.vertx.core.Future
import sttp.monad.MonadError
import sttp.tapir.server.vertx.interpreters.{FromVFuture, VertxCatsServerInterpreter, VertxFutureServerInterpreter, VertxZioServerInterpreter}
import zio.{RIO, Task}

import scala.concurrent.{Promise, Future => SFuture}

package object vertx {

  object VertxZioServerInterpreter {
    def apply[R](serverOptions: VertxZioServerOptions[RIO[R, *]] = VertxZioServerOptions.default[R]): VertxZioServerInterpreter[R] = {
      new VertxZioServerInterpreter[R] {
        override def vertxZioServerOptions: VertxZioServerOptions[RIO[R, *]] = serverOptions
      }
    }

    private[vertx] implicit def monadError[R]: MonadError[RIO[R, *]] = new MonadError[RIO[R, *]] {
      override def unit[T](t: T): RIO[R, T] = Task.succeed(t)
      override def map[T, T2](fa: RIO[R, T])(f: T => T2): RIO[R, T2] = fa.map(f)
      override def flatMap[T, T2](fa: RIO[R, T])(f: T => RIO[R, T2]): RIO[R, T2] = fa.flatMap(f)
      override def error[T](t: Throwable): RIO[R, T] = Task.fail(t)
      override protected def handleWrappedError[T](rt: RIO[R, T])(h: PartialFunction[Throwable, RIO[R, T]]): RIO[R, T] = rt.catchSome(h)
      override def eval[T](t: => T): RIO[R, T] = Task.effect(t)
      override def suspend[T](t: => RIO[R, T]): RIO[R, T] = RIO.effectSuspend(t)
      override def flatten[T](ffa: RIO[R, RIO[R, T]]): RIO[R, T] = ffa.flatten
      override def ensure[T](f: RIO[R, T], e: => RIO[R, Unit]): RIO[R, T] = f.ensuring(e.catchAll(_ => Task.unit))
    }

    private[vertx] class RioFromVFuture[R] extends FromVFuture[RIO[R, *]] {
      def apply[T](f: => Future[T]): RIO[R, T] = f.asRIO
    }

    implicit class VertxFutureToRIO[A](f: => Future[A]) {
      def asRIO[R]: RIO[R, A] = {
        RIO.effectAsync { cb =>
          f.onComplete { handler =>
            if (handler.succeeded()) {
              cb(Task.succeed(handler.result()))
            } else {
              cb(Task.fail(handler.cause()))
            }
          }
        }
      }
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

  object VertxFutureServerInterpreter {
    def apply(serverOptions: VertxFutureServerOptions = VertxFutureServerOptions.default): VertxFutureServerInterpreter = {
      new VertxFutureServerInterpreter {
        override def vertxFutureServerOptions: VertxFutureServerOptions = serverOptions
      }
    }

    private[vertx] object FutureFromVFuture extends FromVFuture[SFuture] {
      def apply[T](f: => Future[T]): SFuture[T] = f.asScala
    }

    implicit class VertxFutureToScalaFuture[A](future: => Future[A]) {
      def asScala: SFuture[A] = {
        val promise = Promise[A]()
        future.onComplete { handler =>
          if (handler.succeeded()) {
            promise.success(handler.result())
          } else {
            promise.failure(handler.cause())
          }
        }
        promise.future
      }
    }
  }
}
