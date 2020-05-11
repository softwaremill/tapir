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
  protected def handleWrappedError[T](rt: F[T])(h: PartialFunction[Throwable, F[T]]): F[T]
  def handleError[T](rt: => F[T])(h: PartialFunction[Throwable, F[T]]): F[T] = {
    Try(rt) match {
      case Success(v)                     => handleWrappedError(v)(h)
      case Failure(e) if h.isDefinedAt(e) => h(e)
      case Failure(e)                     => error(e)
    }
  }
}

object MonadError {
  def recoverErrors[I, E, O, F[_]](
      f: I => F[O]
  )(implicit eClassTag: ClassTag[E], eIsThrowable: E <:< Throwable): MonadError[F] => I => F[Either[E, O]] = { implicit monad => i =>
    import sttp.tapir.monad.syntax._
    monad.handleError(f(i).map(Right(_): Either[E, O])) {
      case e if eClassTag.runtimeClass.isInstance(e) => (Left(e.asInstanceOf[E]): Either[E, O]).unit
    }
  }
}

class FutureMonadError(implicit ec: ExecutionContext) extends MonadError[Future] {
  override def unit[T](t: T): Future[T] = Future.successful(t)
  override def map[T, T2](fa: Future[T])(f: (T) => T2): Future[T2] = fa.map(f)
  override def flatMap[T, T2](fa: Future[T])(f: (T) => Future[T2]): Future[T2] = fa.flatMap(f)
  override def error[T](t: Throwable): Future[T] = Future.failed(t)
  override protected def handleWrappedError[T](rt: Future[T])(h: PartialFunction[Throwable, Future[T]]): Future[T] = rt.recoverWith(h)
}
