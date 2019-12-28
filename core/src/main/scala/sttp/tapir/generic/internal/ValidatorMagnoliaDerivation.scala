package sttp.tapir.generic.internal

import com.github.ghik.silencer.silent
import magnolia.{CaseClass, Magnolia, SealedTrait}
import sttp.tapir.Validator
import sttp.tapir.generic.Configuration

trait ValidatorMagnoliaDerivation {
  type Typeclass[T] = Validator[T]

  def combine[T](ctx: CaseClass[Validator, T])(implicit genericDerivationConfig: Configuration): Validator[T] = {
    Validator.Product(ctx.parameters.map { p =>
      p.label -> new Validator.ProductField[T] {
        override type FieldType = p.PType
        override def name: Validator.FieldName = Validator.FieldName(p.label, genericDerivationConfig.toLowLevelName(p.label))
        override def get(t: T): FieldType = p.dereference(t)
        override def validator: Typeclass[FieldType] = p.typeclass
      }
    }.toMap)
  }

  @silent("never used")
  def dispatch[T](ctx: SealedTrait[Validator, T]): Validator[T] = Validator.Coproduct(ctx)

  implicit def validatorForCaseClass[T]: Validator[T] = macro Magnolia.gen[T]

  def fallback[T]: Validator[T] = Validator.pass
}
