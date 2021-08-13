package sttp.tapir.integ.cats

import cats.Foldable
import sttp.tapir.{ValidationError, ValidationResult, Validator}

object ValidatorCats {
  def nonEmptyFoldable[F[_]: Foldable, T]: Validator.Custom[F[T]] = Validator.Custom[F[T]]("collection != empty") {
    case fa if Foldable[F].isEmpty(fa) =>
      ValidationResult.Invalid(fa, List(ValidationError.expectedTo("be non empty collection", "'empty'")))
    case fa => ValidationResult.Valid(fa)
  }
}
