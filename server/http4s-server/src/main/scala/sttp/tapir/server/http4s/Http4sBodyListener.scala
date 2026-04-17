package sttp.tapir.server.http4s

import cats.effect.kernel.Resource.ExitCase._
import cats.Applicative
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.server.interpreter.BodyListener

import scala.util.{Failure, Success, Try}

class Http4sBodyListener[F[_]](implicit m: MonadError[F], a: Applicative[F]) extends BodyListener[F, Http4sResponseBody[F]] {
  override def onComplete(body: Http4sResponseBody[F])(cb: Try[Unit] => F[Unit]): F[Http4sResponseBody[F]] = {
    body match {
      case ws @ Left(_)                   => cb(Success(())).map(_ => ws)
      case Right((entity, contentLength)) =>
        m.unit(
          Right(
            (
              entity.onFinalizeCase {
                case Succeeded | Canceled => cb(Success(()))
                case Errored(ex)          => cb(Failure(ex))
              },
              contentLength
            )
          )
        )
    }
  }
}
