package sttp.tapir.server.http4s.ztapir

import sttp.tapir.monad.MonadError
import zio.{RIO, URIO}

private[ztapir] class ZIOMonadError[R] extends MonadError[RIO[R, *]] {
  override def unit[T](t: T): RIO[R, T] = URIO.succeed(t)
  override def map[T, T2](fa: RIO[R, T])(f: T => T2): RIO[R, T2] = fa.map(f)
  override def flatMap[T, T2](fa: RIO[R, T])(f: T => RIO[R, T2]): RIO[R, T2] = fa.flatMap(f)
  override def error[T](t: Throwable): RIO[R, T] = RIO.fail(t)
  override protected def handleWrappedError[T](rt: RIO[R, T])(h: PartialFunction[Throwable, RIO[R, T]]): RIO[R, T] = rt.catchSome(h)
}
