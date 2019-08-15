package tapir.generic

import magnolia.{CaseClass, Magnolia, SealedTrait}

trait ValidateMagnoliaDerivation extends LowPriorityValidators {
  type Typeclass[T] = Validator[T]

  def combine[T](ctx: CaseClass[Validator, T]): Validator[T] = {
    ProductValidator(ctx.parameters.map { p =>
      p.label -> new FieldValidator[T] {
        override type fType = p.PType
        override def get(t: T): fType = { p.dereference(t) }
        override def validator: Typeclass[fType] = p.typeclass
      }
    }.toMap)
  }

  def dispatch[T](ctx: SealedTrait[Validator, T]): Validator[T] = Validator.rejecting

  implicit def gen[T]: Validator[T] = macro Magnolia.gen[T]
}

trait LowPriorityValidators {
  def fallback[T]: Validator[T] = Validator.passing
}

trait Validator[T] { outer =>
  def validate(t: T): Boolean
  def map[TT](g: TT => T): Validator[TT] = (t: TT) => {
    outer.validate(g(t))
  }
  def forOption: Validator[Option[T]] = (t: Option[T]) => {
    t.forall(outer.validate)
  }
}

object Validator extends ValidateMagnoliaDerivation {
  def passing[T]: Validator[T] = (t: T) => true
  def rejecting[T]: Validator[T] = (t: T) => false
}

case class ProductValidator[T](fields: Map[String, FieldValidator[T]]) extends Validator[T] {
  override def validate(t: T): Boolean = {
    fields.values.forall { f =>
      f.validator.validate(f.get(t))
    }
  }
}

trait FieldValidator[T] {
  type fType
  def get(t: T): fType
  def validator: Validator[fType]
}

case class ValueValidator[T](constraints: List[Constraint[T]]) extends Validator[T] {
  override def validate(t: T): Boolean = constraints.forall(_.check(t))
}

trait Constraint[T] {
  def check(t: T): Boolean
}

object Constraint {
  case class Minimum[T: Numeric](value: T) extends Constraint[T] {
    override def check(actual: T): Boolean = implicitly[Numeric[T]].gteq(actual, value)
  }

  case class Pattern[T <: String](value: String) extends Constraint[T] {
    override def check(t: T): Boolean = {
      t.matches(value)
    }
  }

  case class MinSize[T <: Iterable[_]](value: Int) extends Constraint[T] {
    override def check(t: T): Boolean = {
      t.size >= value
    }
  }
}
