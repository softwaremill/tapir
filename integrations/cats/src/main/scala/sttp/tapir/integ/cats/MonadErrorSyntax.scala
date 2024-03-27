package sttp.tapir.integ.cats

import cats.~>
import sttp.monad.MonadError

trait MonadErrorSyntax {
  implicit class MonadErrorImapK[F[_]](mef: MonadError[F]) {
    def imapK[G[_]](fk: F ~> G)(gK: G ~> F): MonadError[G] =
      new MonadError[G] {
        override def unit[T](t: T): G[T] = fk(mef.unit(t))

        override def map[T, T2](fa: G[T])(f: T => T2): G[T2] = fk(mef.map(gK(fa))(f))

        override def flatMap[T, T2](fa: G[T])(f: T => G[T2]): G[T2] = fk(mef.flatMap(gK(fa))(f.andThen(gK(_))))

        override def error[T](t: Throwable): G[T] = fk(mef.error(t))

        override protected def handleWrappedError[T](rt: G[T])(h: PartialFunction[Throwable, G[T]]): G[T] =
          fk(mef.handleError(gK(rt)) {
            case t if h.isDefinedAt(t) => gK(h(t))
          })

        override def ensure[T](f: G[T], e: => G[Unit]): G[T] = fk(mef.ensure(gK(f), gK(e)))

        override def blocking[T](t: => T): G[T] = fk(mef.blocking(t))
      }
  }
}

object MonadErrorSyntax extends MonadErrorSyntax
