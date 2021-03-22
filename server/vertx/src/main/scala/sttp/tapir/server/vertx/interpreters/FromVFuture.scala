package sttp.tapir.server.vertx.interpreters

import io.vertx.core.Future

trait FromVFuture[F[_]] {
  def apply[T](f: => Future[T]): F[T]
}
