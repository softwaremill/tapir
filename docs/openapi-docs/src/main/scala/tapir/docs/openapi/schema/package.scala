package tapir.docs.openapi

import tapir.{Validator, Schema => TSchema}

package object schema {
  type SchemaKey = String

  type ObjectTypeData[T] = TypeData[TSchema.SObject, T]
  type AnyTypeData[T] = TypeData[TSchema, T]

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
      case Validator.Mapped(wrapped, _)       => asPrimitiveValidators(wrapped)
      case Validator.All(validators)          => validators.flatMap(asPrimitiveValidators)
      case Validator.Any(validators)          => validators.flatMap(asPrimitiveValidators)
      case Validator.CollectionElements(_, _) => Nil
      case Validator.Product(_)               => Nil
      case Validator.OpenProduct(_)           => Nil
      case bv: Validator.Primitive[_]         => List(bv)
    }
  }
}
