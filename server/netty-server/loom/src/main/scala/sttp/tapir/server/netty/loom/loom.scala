package sttp.tapir.server.netty

import sttp.monad.MonadError

package object loom:
  type Id[X] = X
  type IdRoute = Route[Id]

  private[loom] implicit val idMonad: MonadError[Id] = new MonadError[Id] {
    override def unit[T](t: T): Id[T] = t
    override def map[T, T2](fa: Id[T])(f: T => T2): Id[T2] = f(fa)
    override def flatMap[T, T2](fa: Id[T])(f: T => Id[T2]): Id[T2] = f(fa)
    override def error[T](t: Throwable): Id[T] = throw t
    override protected def handleWrappedError[T](rt: Id[T])(h: PartialFunction[Throwable, Id[T]]): Id[T] = rt
    override def eval[T](t: => T): Id[T] = t
    override def ensure[T](f: Id[T], e: => Id[Unit]): Id[T] =
      try f
      finally e
  }
