package sttp.tapir.server.finatra.cats

import cats.effect.Async
import cats.effect.std.Dispatcher
import com.twitter.inject.Logging
import com.twitter.util.{Future, Promise}
import sttp.monad.MonadError
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.finatra.{FinatraRoute, FinatraServerInterpreter, FinatraServerOptions}

import scala.concurrent.{ExecutionContext, Future => ScalaFuture}
import scala.util.{Failure, Success}

trait FinatraCatsServerInterpreter[F[_]] extends Logging {

  implicit def fa: Async[F]

  def finatraCatsServerOptions: FinatraCatsServerOptions[F]

  def toRoute(
      e: ServerEndpoint[Any, F]
  ): FinatraRoute = {
    FinatraServerInterpreter(
      FinatraServerOptions(finatraCatsServerOptions.createFile, finatraCatsServerOptions.deleteFile, finatraCatsServerOptions.interceptors)
    ).toRoute(
      e.endpoint
        .serverSecurityLogic { a =>
          val scalaFutureResult = finatraCatsServerOptions.dispatcher.unsafeToFuture(e.securityLogic(CatsMonadError)(a))
          scalaFutureResult.asTwitter(cats.effect.unsafe.implicits.global.compute)
        }
        .serverLogic { u => i =>
          val scalaFutureResult = finatraCatsServerOptions.dispatcher.unsafeToFuture(e.logic(CatsMonadError)(u)(i))
          scalaFutureResult.asTwitter(cats.effect.unsafe.implicits.global.compute)
        }
    )
  }

  private object CatsMonadError extends MonadError[F] {
    override def unit[T](t: T): F[T] = Async[F].pure(t)
    override def map[T, T2](ft: F[T])(f: T => T2): F[T2] = Async[F].map(ft)(f)
    override def flatMap[T, T2](ft: F[T])(f: T => F[T2]): F[T2] = Async[F].flatMap(ft)(f)
    override def error[T](t: Throwable): F[T] = Async[F].raiseError(t)
    override protected def handleWrappedError[T](rt: F[T])(h: PartialFunction[Throwable, F[T]]): F[T] = Async[F].recoverWith(rt)(h)
    override def eval[T](t: => T): F[T] = Async[F].delay(t)
    override def suspend[T](t: => F[T]): F[T] = Async[F].defer(t)
    override def flatten[T](ffa: F[F[T]]): F[T] = Async[F].flatten(ffa)
    override def ensure[T](f: F[T], e: => F[Unit]): F[T] = Async[F].guaranteeCase(f)(_ => e)
  }

  /** Convert from a Scala Future to a Twitter Future Source: https://twitter.github.io/util/guide/util-cookbook/futures.html
    */
  private implicit class RichScalaFuture[A](val sf: ScalaFuture[A]) {
    def asTwitter(implicit e: ExecutionContext): Future[A] = {
      val promise: Promise[A] = new Promise[A]()
      sf.onComplete {
        case Success(value)     => promise.setValue(value)
        case Failure(exception) => promise.setException(exception)
      }
      promise
    }
  }
}

object FinatraCatsServerInterpreter {
  def apply[F[_]](
      dispatcher: Dispatcher[F]
  )(implicit _fa: Async[F]): FinatraCatsServerInterpreter[F] = {
    new FinatraCatsServerInterpreter[F] {
      override implicit def fa: Async[F] = _fa
      override def finatraCatsServerOptions: FinatraCatsServerOptions[F] = FinatraCatsServerOptions.default(dispatcher)
    }
  }

  def apply[F[_]](serverOptions: FinatraCatsServerOptions[F])(implicit _fa: Async[F]): FinatraCatsServerInterpreter[F] = {
    new FinatraCatsServerInterpreter[F] {
      override implicit def fa: Async[F] = _fa
      override def finatraCatsServerOptions: FinatraCatsServerOptions[F] = serverOptions
    }
  }
}
