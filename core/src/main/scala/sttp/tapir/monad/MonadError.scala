package sttp.tapir.monad

import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

trait MonadError[F[_]] {
  def unit[T](t: T): F[T]
  def map[T, T2](fa: F[T])(f: T => T2): F[T2]
  def flatMap[T, T2](fa: F[T])(f: T => F[T2]): F[T2]

  def error[T](t: Throwable): F[T]
  def handleError[T](rt: => F[T])(h: PartialFunction[Throwable, F[T]]): F[T]
}

object MonadError {
  def recoverErrors[I, E, O, F[_]](
      f: I => F[O]
  )(implicit eClassTag: ClassTag[E], eIsThrowable: E <:< Throwable): MonadError[F] => I => F[Either[E, O]] = { implicit monad => i =>
    import sttp.tapir.monad.syntax._
    Try(f(i).map(Right(_): Either[E, O])) match {
      case Success(value) =>
        monad.handleError(value) {
          case e if eClassTag.runtimeClass.isInstance(e) => wrapException(e)
        }
      case Failure(exception) if eClassTag.runtimeClass.isInstance(exception) => wrapException(exception)
      case Failure(exception)                                                 => monad.error(exception)
    }
  }

  private def wrapException[F[_], O, E, I](exception: Throwable)(implicit me: MonadError[F]): F[Either[E, O]] = {
    me.unit(Left(exception.asInstanceOf[E]): Either[E, O])
  }
}

class FutureMonadError(implicit ec: ExecutionContext) extends MonadError[Future] {
  override def unit[T](t: T): Future[T] = Future.successful(t)
  override def map[T, T2](fa: Future[T])(f: (T) => T2): Future[T2] = fa.map(f)
  override def flatMap[T, T2](fa: Future[T])(f: (T) => Future[T2]): Future[T2] = fa.flatMap(f)
  override def error[T](t: Throwable): Future[T] = Future.failed(t)
  override def handleError[T](rt: => Future[T])(h: PartialFunction[Throwable, Future[T]]): Future[T] = rt.recoverWith(h)
}
