package sttp.tapir.ztapir

import sttp.monad.MonadError
import zio.{RIO, URIO}

class RIOMonadError[R] extends MonadError[RIO[R, *]] {
  override def unit[T](t: T): RIO[R, T] = URIO.succeed(t)
  override def map[T, T2](fa: RIO[R, T])(f: T => T2): RIO[R, T2] = fa.map(f)
  override def flatMap[T, T2](fa: RIO[R, T])(f: T => RIO[R, T2]): RIO[R, T2] = fa.flatMap(f)
  override def error[T](t: Throwable): RIO[R, T] = RIO.fail(t)
  override protected def handleWrappedError[T](rt: RIO[R, T])(h: PartialFunction[Throwable, RIO[R, T]]): RIO[R, T] = rt.catchSome(h)
  override def eval[T](t: => T): RIO[R, T] = RIO.effect(t)
  override def suspend[T](t: => RIO[R, T]): RIO[R, T] = RIO.effectSuspend(t)
  override def flatten[T](ffa: RIO[R, RIO[R, T]]): RIO[R, T] = ffa.flatten
  override def ensure[T](f: RIO[R, T], e: => RIO[R, Unit]): RIO[R, T] = f.ensuring(e.ignore)
}
