package sttp.tapir.integ.cats

import cats.Foldable
import sttp.tapir.Validator

object ValidatorCats {

  def nonEmptyFoldable[F[_]: Foldable, T]: Validator[F[T]] =
    Validator.nonEmpty[T, List].contramap[F[T]](Foldable[F].toList)
}
