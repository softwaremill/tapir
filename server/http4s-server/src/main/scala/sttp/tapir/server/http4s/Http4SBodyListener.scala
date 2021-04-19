package sttp.tapir.server.http4s

import cats.Applicative
import sttp.monad.MonadError
import sttp.tapir.server.interpreter.BodyListener

class Http4SBodyListener[F[_], G[_]](implicit m: MonadError[G], a: Applicative[F]) extends BodyListener[G, Http4sResponseBody[F]] {
  override def onComplete(body: Http4sResponseBody[F])(cb: => G[Unit]): Http4sResponseBody[F] = {
    body match {
      case ws @ Left(_) =>
        cb
        ws
      case Right(entity) => Right(entity.onFinalize(a.point(cb))) // todo
    }
  }
}
