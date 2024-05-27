package sttp.tapir.server.netty.internal

import sttp.shared.Identity

import scala.concurrent.Future

trait RunAsync[F[_]] {
  def apply(f: => F[Unit]): Unit
}
object RunAsync {
  final val Id: RunAsync[Identity] = new RunAsync[Identity] {
    override def apply(f: => Identity[Unit]): Unit = f
  }

  final val Future: RunAsync[Future] = new RunAsync[Future] {
    override def apply(f: => Future[Unit]): Unit =
      f: Unit
  }
}
