package sttp.tapir.server.http4s

import cats.effect.kernel.Resource.ExitCase._
import cats.{Applicative, ~>}
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.server.interpreter.BodyListener

import scala.util.{Failure, Success, Try}

class Http4sBodyListener[F[_], G[_]](gToF: G ~> F)(implicit m: MonadError[G], a: Applicative[F])
    extends BodyListener[G, Http4sResponseBody[F]] {
  override def onComplete(body: Http4sResponseBody[F])(cb: Try[Unit] => G[Unit]): G[Http4sResponseBody[F]] = {
    body match {
      case ws @ Left(_) => cb(Success(())).map(_ => ws)
      case Right((entity, contentLength)) =>
        m.unit(
          Right(
            (
              entity.onFinalizeCase {
                case Succeeded | Canceled => gToF(cb(Success(())))
                case Errored(ex)          => gToF(cb(Failure(ex)))
              },
              contentLength
            )
          )
        )
    }
  }
}
