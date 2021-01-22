package sttp.tapir.server.finatra.cats

import cats.effect.Effect
import com.twitter.inject.Logging
import com.twitter.util.Future
import io.catbird.util.Rerunnable
import io.catbird.util.effect._
import sttp.capabilities.{Effect => SttpEffect}
import sttp.monad.MonadError
import sttp.tapir.Endpoint
import sttp.tapir.internal.{ChangeEffect, FunctionK}
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.finatra.{FinatraRoute, FinatraServerInterpreter, FinatraServerOptions}

import scala.reflect.ClassTag

trait FinatraCatsServerInterpreter extends Logging {
  def toRoute[I, E, O, F[_]](
      e: Endpoint[I, E, O, SttpEffect[F]]
  )(logic: I => F[Either[E, O]])(implicit serverOptions: FinatraServerOptions, eff: Effect[F]): FinatraRoute = {
    toRoute(e.serverLogic(logic))
  }

  def toRouteRecoverErrors[I, E, O, F[_]](e: Endpoint[I, E, O, SttpEffect[F]])(logic: I => F[O])(implicit
      eIsThrowable: E <:< Throwable,
      eClassTag: ClassTag[E],
      eff: Effect[F]
  ): FinatraRoute = {
    toRoute(e.serverLogicRecoverErrors(logic))
  }

  def toRoute[I, E, O, F[_]](
      e: ServerEndpoint[I, E, O, SttpEffect[F], F]
  )(implicit serverOptions: FinatraServerOptions, eff: Effect[F]): FinatraRoute = {
    val fMonad = new CatsMonadError[F]
    val eFuture: Endpoint[I, E, O, SttpEffect[Future]] = ChangeEffect[F, Future, I, E, O, Any](
      e.endpoint,
      new FunctionK[F, Future] {
        override def apply[A](fa: F[A]): Future[A] = eff.toIO(fa).to[Rerunnable].run
      },
      fMonad
    )
    FinatraServerInterpreter.toRoute(eFuture.serverLogic(i => eff.toIO(e.logic(fMonad)(i)).to[Rerunnable].run))
  }

  private class CatsMonadError[F[_]](implicit F: Effect[F]) extends MonadError[F] {
    override def unit[T](t: T): F[T] = F.pure(t)
    override def map[T, T2](fa: F[T])(f: T => T2): F[T2] = F.map(fa)(f)
    override def flatMap[T, T2](fa: F[T])(f: T => F[T2]): F[T2] = F.flatMap(fa)(f)
    override def error[T](t: Throwable): F[T] = F.raiseError(t)
    override protected def handleWrappedError[T](rt: F[T])(h: PartialFunction[Throwable, F[T]]): F[T] = F.recoverWith(rt)(h)
    override def eval[T](t: => T): F[T] = F.delay(t)
    override def suspend[T](t: => F[T]): F[T] = F.suspend(t)
    override def flatten[T](ffa: F[F[T]]): F[T] = F.flatten(ffa)
    override def ensure[T](f: F[T], e: => F[Unit]): F[T] = F.guarantee(f)(e)
  }
}

object FinatraCatsServerInterpreter extends FinatraCatsServerInterpreter
