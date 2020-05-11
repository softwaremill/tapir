package sttp.tapir.monad

object syntax {
  implicit final class MonadOps[F[_], A](private val r: F[A]) extends AnyVal {
    def map[B](f: A => B)(implicit ME: Monad[F]): F[B] = ME.map(r)(f)
    def flatMap[B](f: A => F[B])(implicit ME: Monad[F]): F[B] = ME.flatMap(r)(f)
  }

  implicit final class MonadValueOps[F[_], A](private val v: A) extends AnyVal {
    def unit(implicit ME: Monad[F]): F[A] = ME.unit(v)
  }
}
