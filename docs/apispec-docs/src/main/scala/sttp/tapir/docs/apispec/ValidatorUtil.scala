package sttp.tapir.docs.apispec

import sttp.tapir.Validator

private[docs] object ValidatorUtil {
  private[docs] def asPrimitiveValidators(v: Validator[_]): Seq[Validator.Primitive[_]] = {
    v match {
      case Validator.Mapped(wrapped, _) => asPrimitiveValidators(wrapped)
      case Validator.All(validators)    => validators.flatMap(asPrimitiveValidators)
      case Validator.Any(validators)    => validators.flatMap(asPrimitiveValidators)
      case Validator.Custom(_, _)       => Nil
      case bv: Validator.Primitive[_]   => List(bv)
    }
  }
}
