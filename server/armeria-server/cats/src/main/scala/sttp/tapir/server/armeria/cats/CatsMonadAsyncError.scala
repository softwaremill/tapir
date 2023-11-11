package sttp.tapir.server.armeria.cats

import cats.effect.Async
import cats.syntax.functor._
import sttp.monad.{Canceler, MonadAsyncError}
import sttp.tapir.integ.cats.effect.CatsMonadError

// Forked from sttp.client3.impl.cats.CatsMonadAsyncError
private class CatsMonadAsyncError[F[_]](implicit F: Async[F]) extends CatsMonadError[F] with MonadAsyncError[F] {
  override def async[T](register: ((Either[Throwable, T]) => Unit) => Canceler): F[T] =
    F.async(cb => F.delay(register(cb)).map(c => Some(F.delay(c.cancel()))))

  override def blocking[T](t: => T): F[T] =
    F.blocking(t)
}
