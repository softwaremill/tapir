package sttp.tapir.server.finatra.cats

import cats.effect.Async
import cats.effect.std.Dispatcher
import com.twitter.inject.Logging
import com.twitter.util.{Future, Promise}
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.finatra.{FinatraRoute, FinatraServerInterpreter, FinatraServerOptions}
import sttp.tapir.integ.cats.CatsMonadError

import scala.concurrent.{ExecutionContext, Future => ScalaFuture}
import scala.util.{Failure, Success}

trait FinatraCatsServerInterpreter[F[_]] extends Logging {

  implicit def fa: Async[F]

  def finatraCatsServerOptions: FinatraCatsServerOptions[F]

  def toRoute(
      e: ServerEndpoint[Any, F]
  ): FinatraRoute = {
    FinatraServerInterpreter(
      FinatraServerOptions(finatraCatsServerOptions.createFile, finatraCatsServerOptions.deleteFile, finatraCatsServerOptions.interceptors)
    ).toRoute(
      e.endpoint
        .serverSecurityLogic { a =>
          val scalaFutureResult = finatraCatsServerOptions.dispatcher.unsafeToFuture(e.securityLogic(new CatsMonadError[F])(a))
          scalaFutureResult.asTwitter(cats.effect.unsafe.implicits.global.compute)
        }
        .serverLogic { u => i =>
          val scalaFutureResult = finatraCatsServerOptions.dispatcher.unsafeToFuture(e.logic(new CatsMonadError[F])(u)(i))
          scalaFutureResult.asTwitter(cats.effect.unsafe.implicits.global.compute)
        }
    )
  }

  /** Convert from a Scala Future to a Twitter Future Source: https://twitter.github.io/util/guide/util-cookbook/futures.html
    */
  private implicit class RichScalaFuture[A](val sf: ScalaFuture[A]) {
    def asTwitter(implicit e: ExecutionContext): Future[A] = {
      val promise: Promise[A] = new Promise[A]()
      sf.onComplete {
        case Success(value)     => promise.setValue(value)
        case Failure(exception) => promise.setException(exception)
      }
      promise
    }
  }
}

object FinatraCatsServerInterpreter {
  def apply[F[_]](
      dispatcher: Dispatcher[F]
  )(implicit _fa: Async[F]): FinatraCatsServerInterpreter[F] = {
    new FinatraCatsServerInterpreter[F] {
      override implicit def fa: Async[F] = _fa
      override def finatraCatsServerOptions: FinatraCatsServerOptions[F] = FinatraCatsServerOptions.default(dispatcher)
    }
  }

  def apply[F[_]](serverOptions: FinatraCatsServerOptions[F])(implicit _fa: Async[F]): FinatraCatsServerInterpreter[F] = {
    new FinatraCatsServerInterpreter[F] {
      override implicit def fa: Async[F] = _fa
      override def finatraCatsServerOptions: FinatraCatsServerOptions[F] = serverOptions
    }
  }
}
