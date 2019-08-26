package tapir

import tapir.generic.ValidateMagnoliaDerivation

sealed trait Validator[T] { outer =>
  def validate(t: T): Boolean
  def unwrap: Validator[_] = outer
  def contramap[TT](g: TT => T): Validator[TT] = new Validator[TT] {
    override def validate(t: TT): Boolean = outer.validate(g(t))
    override def unwrap: Validator[_] = outer.unwrap
  }

  def toOption: Validator[Option[T]] = OptionValidator(outer)
  def toArray: Validator[Array[T]] = ArrayValidator(outer, List.empty[Constraint[Array[T]]])(outer)
  def toIterable[C[X] <: Iterable[X]]: Validator[C[T]] = CollectionValidator[T, C](outer, List.empty[Constraint[C[T]]])(outer)
}

object Validator extends ValidateMagnoliaDerivation {
  def passing[T]: Validator[T] = PassingValidator()
  def rejecting[T]: Validator[T] = RejectingValidator()

  implicit def validatorForOption[T: Validator]: Validator[Option[T]] = implicitly[Validator[T]].toOption
  implicit def validatorForArray[T: Validator]: Validator[Array[T]] = implicitly[Validator[T]].toArray
  implicit def validatorForIterable[T: Validator, C[X] <: Iterable[X]]: Validator[C[T]] = implicitly[Validator[T]].toIterable[C]
  implicit def validatorForMap[T: Validator]: Validator[Map[String, T]] = OpenProductValidator(implicitly[Validator[T]])
}

case class PassingValidator[T]() extends Validator[T] {
  override def validate(t: T): Boolean = true
}

case class RejectingValidator[T]() extends Validator[T] {
  override def validate(t: T): Boolean = true
}

case class OptionValidator[T](inner: Validator[T]) extends Validator[Option[T]] {
  override def validate(t: Option[T]): Boolean = t.forall(inner.validate)
}

case class ProductValidator[T](fields: Map[String, FieldValidator[T]]) extends Validator[T] {
  override def validate(t: T): Boolean = {
    fields.values.forall { f =>
      f.validator.validate(f.get(t))
    }
  }
}

trait FieldValidator[T] {
  type FieldType
  def get(t: T): FieldType
  def validator: Validator[FieldType]
}

case class ValueValidator[T](constraints: List[Constraint[T]]) extends Validator[T] {
  override def validate(t: T): Boolean = constraints.forall(_.check(t))
}

sealed trait BaseCollectionValidator[E, C[_]] extends Validator[C[E]] {
  def elementValidator: Validator[E]
  def constraints: List[Constraint[C[E]]]
}

object BaseCollectionValidator {
  def unapply(arg: BaseCollectionValidator[_, CC forSome { type CC[_] }]): Option[(Validator[_], List[Constraint[_]])] = {
    Some(arg.elementValidator -> arg.constraints)
  }
}

case class CollectionValidator[E: Validator, C[X] <: Iterable[X]](elementValidator: Validator[E], constraints: List[Constraint[C[E]]])
    extends BaseCollectionValidator[E, C] {
  override def validate(t: C[E]): Boolean = {
    t.forall(elementValidator.validate) && constraints.forall(_.check(t))
  }
}

case class ArrayValidator[E: Validator](elementValidator: Validator[E], constraints: List[Constraint[Array[E]]])
    extends BaseCollectionValidator[E, Array] {
  override def validate(t: Array[E]): Boolean = {
    t.forall(elementValidator.validate) && constraints.forall(_.check(t))
  }
}

case class OpenProductValidator[E](elementValidator: Validator[E]) extends Validator[Map[String, E]] {
  override def validate(t: Map[String, E]): Boolean = {
    t.values.forall(elementValidator.validate)
  }
}
