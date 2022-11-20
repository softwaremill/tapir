package sttp.tapir.server.vertx.interpreters

trait RunAsync[F[_]] {
  def apply[T](f: => F[T]): Unit
}
