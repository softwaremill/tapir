package sttp.tapir.server.netty.internal

trait RunAsync[F[_]] {
  def apply(f: => F[Unit]): Unit
}
