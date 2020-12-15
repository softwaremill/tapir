package sttp.tapir.docs.apispec

import sttp.tapir.Validator

private[docs] object ValidatorUtil {
  private[docs] def elementValidator(v: Validator[_]): Validator[_] = {
    val result = asSingleValidators(v).collect {
      case Validator.OpenProduct(elementValidator)           => elementValidator
      case Validator.CollectionElements(elementValidator, _) => elementValidator
    }

    Validator.all(result: _*)
  }

  private[docs] def asSingleValidators(v: Validator[_]): Seq[Validator.Single[_]] = {
    v match {
      case Validator.All(validators)    => validators.flatMap(asSingleValidators)
      case Validator.Any(validators)    => validators.flatMap(asSingleValidators)
      case Validator.Mapped(wrapped, _) => asSingleValidators(wrapped)
      case Validator.Ref(_)             => Nil
      case sv: Validator.Single[_]      => List(sv)
    }
  }

  private[docs] def asPrimitiveValidators(v: Validator[_], unwrapCollections: Boolean): Seq[Validator.Primitive[_]] = {
    v match {
      case Validator.Mapped(wrapped, _)            => asPrimitiveValidators(wrapped, unwrapCollections)
      case Validator.All(validators)               => validators.flatMap(asPrimitiveValidators(_, unwrapCollections))
      case Validator.Any(validators)               => validators.flatMap(asPrimitiveValidators(_, unwrapCollections))
      case Validator.CollectionElements(mapped, _) => if (unwrapCollections) asPrimitiveValidators(mapped, unwrapCollections) else Nil
      case Validator.Product(_)                    => Nil
      case Validator.Coproduct(_)                  => Nil
      case Validator.OpenProduct(_)                => Nil
      case Validator.Custom(_, _)                  => Nil
      case Validator.Ref(_)                        => Nil
      case bv: Validator.Primitive[_]              => List(bv)
    }
  }

  private[docs] def fieldValidator(v: Validator[_], fieldName: String): Validator[_] = {
    Validator.all(asSingleValidators(v).collect {
      case Validator.CollectionElements(Validator.Product(fields), _) if fields.isDefinedAt(fieldName) => fields(fieldName).validator
      case Validator.Product(fields) if fields.isDefinedAt(fieldName)                                  => fields(fieldName).validator
    }: _*)
  }
}
