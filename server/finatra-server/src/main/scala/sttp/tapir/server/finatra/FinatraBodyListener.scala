package sttp.tapir.server.finatra

import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.server.interpreter.BodyListener

class FinatraBodyListener[F[_]](implicit m: MonadError[F]) extends BodyListener[F, FinatraContent] {
  override def onComplete(body: FinatraContent)(cb: => F[Unit]): F[FinatraContent] =
    body match {
      case content @ FinatraContentReader(reader) =>
        reader.onClose.ensure(cb)
        m.unit(content)
      case content => cb.map(_ => content)
    }

}
