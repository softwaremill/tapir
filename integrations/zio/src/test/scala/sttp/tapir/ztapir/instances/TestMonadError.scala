package sttp.tapir.ztapir.instances

import sttp.monad.MonadError
import zio.{RIO, ZIO}

object TestMonadError {
  type TestEffect[A] = RIO[Any, A]

  implicit val testEffectMonadError: MonadError[TestEffect] = new MonadError[TestEffect] {
    override def unit[T](t: T): TestEffect[T] = ZIO.succeed(t)

    override def map[T, T2](fa: TestEffect[T])(f: T => T2): TestEffect[T2] = fa.map(f)

    override def flatMap[T, T2](fa: TestEffect[T])(f: T => TestEffect[T2]): TestEffect[T2] = fa.flatMap(f)

    override def error[T](t: Throwable): TestEffect[T] = ZIO.fail(t)

    override protected def handleWrappedError[T](rt: TestEffect[T])(h: PartialFunction[Throwable, TestEffect[T]]): TestEffect[T] =
      rt.catchSome(h)

    override def ensure[T](f: TestEffect[T], e: => TestEffect[Unit]): TestEffect[T] = f.ensuring(e.ignore)
    override def ensure2[T](f: => TestEffect[T], e: => TestEffect[Unit]): TestEffect[T] = ZIO.suspend(f).ensuring(e.ignore)

    override def blocking[T](t: => T): TestEffect[T] = ZIO.attemptBlocking(t)
  }
}
