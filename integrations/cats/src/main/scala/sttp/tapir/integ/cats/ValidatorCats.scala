package sttp.tapir.integ.cats

import cats.Foldable
import sttp.tapir.Validator

object ValidatorCats {

  def nonEmptyFoldable[F[_]: Foldable, T]: Validator[F[T]] =
    Validator.minSize[T, List](1).contramap[F[T]](Foldable[F].toList)
}
