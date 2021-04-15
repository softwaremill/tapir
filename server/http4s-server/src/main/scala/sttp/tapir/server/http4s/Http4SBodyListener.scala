package sttp.tapir.server.http4s

import cats.Applicative
import sttp.monad.MonadError
import sttp.tapir.server.interpreter.BodyListener

class Http4SBodyListener[F[_]](implicit m: MonadError[F], a: Applicative[F]) extends BodyListener[F, Http4sResponseBody[F]] {
  override def listen(body: Http4sResponseBody[F])(cb: => F[Unit]): Http4sResponseBody[F] = {
    body match {
      case ws @ Left(_) =>
        cb
        ws
      case Right(entity) => Right(entity.onFinalize(cb))
    }
  }
}
