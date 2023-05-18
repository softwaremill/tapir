package sttp.tapir.server.finatra.cats

import cats.effect.{Async, Sync}
import cats.syntax.all._
import cats.effect.std.Dispatcher
import com.twitter.util.{Future, Promise}

import scala.util.{Failure, Success}

object conversions {

  /** Convert from a Scala Future to a Twitter Future Source: https://twitter.github.io/util/guide/util-cookbook/futures.html
    */
  private[cats] implicit class RichF[F[_], A](val fa: F[A]) {
    def asTwitterFuture(implicit dispatcher: Dispatcher[F]): Future[A] = {
      val promise: Promise[A] = new Promise[A]()
      dispatcher
        .unsafeToFuture(fa)
        .onComplete {
          case Success(value)     => promise.setValue(value)
          case Failure(exception) => promise.setException(exception)
        }(cats.effect.unsafe.implicits.global.compute)
      promise
    }
  }

  /** Convert from a Twitter Future to some F with Async capabilities. Based on https://typelevel.org/cats-effect/docs/typeclasses/async
    */
   private[cats] def fromFuture[F[_]: Async, A](f: => Future[A]): F[A] = Async[F].async { cb =>
      Sync[F].delay {
        val fut = f.onSuccess(f => cb(Right(f))).onFailure(e => cb(Left(e)))
        Some(Sync[F].delay(fut.raise(new InterruptedException("Fiber canceled"))).void)
      } 
    }
}
