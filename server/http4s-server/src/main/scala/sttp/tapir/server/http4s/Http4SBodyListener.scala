package sttp.tapir.server.http4s

import cats.{Applicative, ~>}
import sttp.monad.MonadError
import sttp.tapir.server.interpreter.BodyListener

class Http4SBodyListener[F[_], G[_]](gToF: G ~> F)(implicit m: MonadError[G], a: Applicative[F])
    extends BodyListener[G, Http4sResponseBody[F]] {
  override def onComplete(body: Http4sResponseBody[F])(cb: => G[Unit]): G[Http4sResponseBody[F]] = {
    body match {
      case ws @ Left(_)  => m.map(cb)(_ => ws)
      case Right(entity) => m.unit(Right(entity.onFinalize(gToF(cb))))
    }
  }
}
