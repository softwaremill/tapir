package sttp.tapir.server.armeria

import scala.concurrent.Future

private[armeria] trait FromFuture[F[_]] {
  def apply[A](f: => Future[A]): F[A]
}

private[armeria] object FromFuture {
  val identity: FromFuture[Future] = new FromFuture[Future] {
    override def apply[A](f: => Future[A]): Future[A] = f
  }
}
