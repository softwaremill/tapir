package sttp.tapir.internal

import sttp.monad.MonadError
import sttp.monad.syntax._

class SequenceSupport[F[_]](implicit me: MonadError[F]) {
  def sequence[T](lt: List[F[T]]): F[List[T]] = lt match {
    case Nil          => (Nil: List[T]).unit
    case head :: tail => head.flatMap(ht => sequence(tail).map(ht :: _))
  }
}
