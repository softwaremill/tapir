package sttp.tapir.server.netty.internal

trait RunAsync[F[_]] {
  def apply[T](f: => F[T]): Unit
}
