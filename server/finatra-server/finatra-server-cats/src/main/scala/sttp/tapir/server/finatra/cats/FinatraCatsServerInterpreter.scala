package sttp.tapir.server.finatra.cats

import cats.effect
import com.twitter.inject.Logging
import io.catbird.util.Rerunnable
import io.catbird.util.effect._
import sttp.monad.MonadError
import sttp.tapir.Endpoint
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.finatra.{FinatraRoute, FinatraServerInterpreter, FinatraServerOptions}

import scala.reflect.ClassTag

trait FinatraCatsServerInterpreter extends Logging {

  def finatraServerOptions: FinatraServerOptions = FinatraServerOptions.default

  def toRoute[I, E, O, F[_]](
      e: Endpoint[I, E, O, Any]
  )(logic: I => F[Either[E, O]])(implicit eff: Effect[F]): FinatraRoute = {
    toRoute(e.serverLogic(logic))
  }

  def toRouteRecoverErrors[I, E, O, F[_]](e: Endpoint[I, E, O, Any])(logic: I => F[O])(implicit
      eIsThrowable: E <:< Throwable,
      eClassTag: ClassTag[E],
      eff: Effect[F]
  ): FinatraRoute = {
    toRoute(e.serverLogicRecoverErrors(logic))
  }

  def toRoute[I, E, O, F[_]](
      e: ServerEndpoint[I, E, O, Any, F]
  )(implicit eff: Effect[F]): FinatraRoute = {
    FinatraServerInterpreter(finatraServerOptions).toRoute(e.endpoint.serverLogic(i => eff.toIO(e.logic(new CatsMonadError)(i)).to[Rerunnable].run))
  }

  private class CatsMonadError[F[_]](implicit F: Effect[F]) extends MonadError[F] {
    override def unit[T](t: T): F[T] = F.pure(t)
    override def map[T, T2](fa: F[T])(f: T => T2): F[T2] = F.map(fa)(f)
    override def flatMap[T, T2](fa: F[T])(f: T => F[T2]): F[T2] = F.flatMap(fa)(f)
    override def error[T](t: Throwable): F[T] = F.raiseError(t)
    override protected def handleWrappedError[T](rt: F[T])(h: PartialFunction[Throwable, F[T]]): F[T] = F.recoverWith(rt)(h)
    override def eval[T](t: => T): F[T] = F.delay(t)
    override def suspend[T](t: => F[T]): F[T] = F.defer(t)
    override def flatten[T](ffa: F[F[T]]): F[T] = F.flatten(ffa)
    override def ensure[T](f: F[T], e: => F[Unit]): F[T] = F.guarantee(f)(e)
  }
}

object FinatraCatsServerInterpreter {
  def apply(serverOptions: FinatraServerOptions = FinatraServerOptions.default): FinatraCatsServerInterpreter = {
    new FinatraCatsServerInterpreter {
      override def finatraServerOptions: FinatraServerOptions = serverOptions
    }
  }
}
