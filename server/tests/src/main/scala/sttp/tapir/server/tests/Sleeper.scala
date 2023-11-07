package sttp.tapir.server.tests

import scala.concurrent.duration._
import scala.concurrent.blocking
import scala.concurrent.Future
import scala.concurrent.ExecutionContext

trait Sleeper[F[_]] {
  def sleep(duration: FiniteDuration): F[Unit]
}

object Sleeper {
  def futureSleeper(implicit ec: ExecutionContext): Sleeper[Future] = new Sleeper[Future] {
    override def sleep(duration: FiniteDuration): Future[Unit] = Future {
      blocking {
        Thread.sleep(duration.toMillis)
      }
    }
  }
}
