package sttp.tapir.server.finatra

import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.server.interpreter.BodyListener

import scala.util.{Failure, Success, Try}

class FinatraBodyListener[F[_]](implicit m: MonadError[F]) extends BodyListener[F, FinatraContent] {
  override def onComplete(body: FinatraContent)(cb: Try[Unit] => F[Unit]): F[FinatraContent] =
    body match {
      case content @ FinatraContentReader(reader) =>
        reader.onClose.onSuccess(_ => cb(Success(())))
        reader.onClose.onFailure(ex => cb(Failure(ex)))
        m.unit(content)
      case content => cb(Success(())).map(_ => content)
    }

}
