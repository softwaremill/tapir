package sttp.tapir.docs.openapi

import sttp.tapir.Validator

package object schema {
  type SchemaKey = String

  private[schema] def elementValidator(v: Validator[_]): Validator[_] = {
    val result = asSingleValidators(v).collect {
      case Validator.OpenProduct(elementValidator)           => elementValidator
      case Validator.CollectionElements(elementValidator, _) => elementValidator
    }

    Validator.all(result: _*)
  }

  private[schema] def asSingleValidators(v: Validator[_]): Seq[Validator.Single[_]] = {
    v match {
      case Validator.All(validators) => validators.flatMap(asSingleValidators)
      case Validator.Any(validators) => validators.flatMap(asSingleValidators)
      case sv: Validator.Single[_]   => List(sv)
    }
  }

  private[schema] def asPrimitiveValidators(v: Validator[_]): Seq[Validator.Primitive[_]] = {
    v match {
      case Validator.Mapped(wrapped, _)             => asPrimitiveValidators(wrapped)
      case Validator.All(validators)                => validators.flatMap(asPrimitiveValidators)
      case Validator.Any(validators)                => validators.flatMap(asPrimitiveValidators)
      case Validator.CollectionElements(wrapped, _) => asPrimitiveValidators(wrapped)
      case Validator.Product(_)                     => Nil
      case Validator.Coproduct(_)                   => Nil
      case Validator.OpenProduct(_)                 => Nil
      case bv: Validator.Primitive[_]               => List(bv)
    }
  }

  private[schema] def fieldValidator(v: Validator[_], fieldName: String): Validator[_] = {
    Validator.all(asSingleValidators(v).collect {
      case Validator.CollectionElements(Validator.Product(fields), _) if fields.isDefinedAt(fieldName) => fields(fieldName).validator
      case Validator.Product(fields) if fields.isDefinedAt(fieldName)                                  => fields(fieldName).validator
    }: _*)
  }
}
