package sttp.tapir.server.finatra

import _root_.cats.effect.Effect
import com.github.ghik.silencer.silent
import com.twitter.inject.Logging
import io.catbird.util.Rerunnable
import io.catbird.util.effect._
import sttp.tapir.Endpoint
import sttp.tapir.monad.MonadError
import sttp.tapir.server.ServerEndpoint

import scala.reflect.ClassTag

package object cats {
  implicit class RichFinatraCatsEndpoint[I, E, O](e: Endpoint[I, E, O, Nothing]) extends Logging {
    def toRoute[F[_]](logic: I => F[Either[E, O]])(implicit serverOptions: FinatraServerOptions, eff: Effect[F]): FinatraRoute = {
      e.serverLogic(logic).toRoute
    }

    @silent("never used")
    def toRouteRecoverErrors[F[_]](logic: I => F[O])(implicit
        eIsThrowable: E <:< Throwable,
        eClassTag: ClassTag[E],
        eff: Effect[F]
    ): FinatraRoute = {
      e.serverLogicRecoverErrors(logic).toRoute
    }
  }

  implicit class RichFinatraCatsServerEndpoint[I, E, O, F[_]](e: ServerEndpoint[I, E, O, Nothing, F]) extends Logging {
    def toRoute(implicit serverOptions: FinatraServerOptions, eff: Effect[F]): FinatraRoute = {
      new RichFinatraServerEndpoint(e.endpoint.serverLogic(i => eff.toIO(e.logic(new CatsMonadError)(i)).to[Rerunnable].run)).toRoute
    }

    private class CatsMonadError(implicit F: _root_.cats.MonadError[F, Throwable]) extends MonadError[F] {
      override def unit[T](t: T): F[T] = F.pure(t)
      override def map[T, T2](fa: F[T])(f: T => T2): F[T2] = F.map(fa)(f)
      override def flatMap[T, T2](fa: F[T])(f: T => F[T2]): F[T2] = F.flatMap(fa)(f)
      override def error[T](t: Throwable): F[T] = F.raiseError(t)
      override protected def handleWrappedError[T](rt: F[T])(h: PartialFunction[Throwable, F[T]]): F[T] = F.recoverWith(rt)(h)
    }
  }
}
