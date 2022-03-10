package sttp.tapir.server.armeria

import scala.concurrent.Future

private[armeria] trait FutureConversion[F[_]] {
  def from[A](f: => Future[A]): F[A]

  def to[A](f: => F[A]): Future[A]
}

private[armeria] object FutureConversion {
  val identity: FutureConversion[Future] = new FutureConversion[Future] {
    override def from[A](f: => Future[A]): Future[A] = f

    override def to[A](f: => Future[A]): Future[A] = f
  }
}
