package tapir.generic

import magnolia.{CaseClass, Magnolia, SealedTrait}
import tapir.{FieldValidator, ProductValidator, Validator}

trait ValidateMagnoliaDerivation {
  type Typeclass[T] = Validator[T]

  def combine[T](ctx: CaseClass[Validator, T]): Validator[T] = {
    ProductValidator(ctx.parameters.map { p =>
      p.label -> new FieldValidator[T] {
        override type FieldType = p.PType
        override def get(t: T): FieldType = { p.dereference(t) }
        override def validator: Typeclass[FieldType] = p.typeclass
      }
    }.toMap)
  }

  def dispatch[T](ctx: SealedTrait[Validator, T]): Validator[T] = Validator.passing

  implicit def gen[T]: Validator[T] = macro Magnolia.gen[T]

  def fallback[T]: Validator[T] = Validator.passing
}
