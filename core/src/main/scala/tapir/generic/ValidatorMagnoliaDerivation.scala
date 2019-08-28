package tapir.generic

import magnolia.{CaseClass, Magnolia, SealedTrait}
import tapir.Validator

trait ValidatorMagnoliaDerivation {
  type Typeclass[T] = Validator[T]

  def combine[T](ctx: CaseClass[Validator, T]): Validator[T] = {
    Validator.Product(ctx.parameters.map { p =>
      p.label -> new Validator.ProductField[T] {
        override type FieldType = p.PType
        override def get(t: T): FieldType = { p.dereference(t) }
        override def validator: Typeclass[FieldType] = p.typeclass
      }
    }.toMap)
  }

  def dispatch[T](ctx: SealedTrait[Validator, T]): Validator[T] = Validator.pass

  implicit def validatorForCaseClass[T]: Validator[T] = macro Magnolia.gen[T]

  def fallback[T]: Validator[T] = Validator.pass
}
