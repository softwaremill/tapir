package sttp.tapir.server.netty.internal

import sttp.monad.MonadError
import sttp.monad.syntax._

// FIXME: Move to sttp-shared after zio 2.0.3 start working ?
class SequenceSupport[F[_]](implicit me: MonadError[F]) {
  def sequence[T](lt: List[F[T]]): F[List[T]] = lt match {
    case Nil          => (Nil: List[T]).unit
    case head :: tail => head.flatMap(ht => sequence(tail).map(ht :: _))
  }
}
