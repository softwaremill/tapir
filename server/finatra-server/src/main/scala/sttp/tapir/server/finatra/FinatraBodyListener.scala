package sttp.tapir.server.finatra

import sttp.monad.MonadError
import sttp.tapir.server.interpreter.BodyListener

class FinatraBodyListener[F[_]](implicit m: MonadError[F]) extends BodyListener[F, FinatraContent] {
  override def listen(body: FinatraContent)(cb: => F[Unit]): FinatraContent =
    body match {
      case content @ FinatraContentReader(reader) =>
        reader.onClose.ensure(cb)
        content
      case content =>
        cb
        content
    }

}
