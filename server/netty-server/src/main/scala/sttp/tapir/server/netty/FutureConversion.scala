package sttp.tapir.server.netty

import scala.concurrent.Future

trait FutureConversion[F[_]] {
  def from[A](f: => Future[A]): F[A]

  def to[A](f: => F[A]): Future[A]
}

object FutureConversion {
  implicit val identity: FutureConversion[Future] = new FutureConversion[Future] {
    override def from[A](f: => Future[A]): Future[A] = f

    override def to[A](f: => Future[A]): Future[A] = f
  }
}
