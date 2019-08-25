package tapir

import tapir.generic.ValidateMagnoliaDerivation

trait Validator[T] { outer =>
  def validate(t: T): Boolean
  def unwrap: Validator[_] = outer
  def contramap[TT](g: TT => T): Validator[TT] = new Validator[TT] {
    override def validate(t: TT): Boolean = outer.validate(g(t))
    override def unwrap: Validator[_] = outer.unwrap
  }

  def toOption: Validator[Option[T]] = (t: Option[T]) => t.forall(outer.validate)
  def toArray: Validator[Array[T]] = ArrayValidator(outer, List.empty[Constraint[Array[T]]])(outer)
  def toIterable[C[_] <: Iterable[_]]: Validator[C[T]] = CollectionValidator[T, C](outer, List.empty[Constraint[C[T]]])(outer)
}

object Validator extends ValidateMagnoliaDerivation {
  def passing[T]: Validator[T] = (_: T) => true
  def rejecting[T]: Validator[T] = (_: T) => false

  implicit def validatorForOption[T: Validator]: Validator[Option[T]] = implicitly[Validator[T]].toOption
  implicit def validatorForArray[T: Validator]: Validator[Array[T]] = implicitly[Validator[T]].toArray
  implicit def validatorForIterable[T: Validator, C[_] <: Iterable[T]]: Validator[C[T]] = implicitly[Validator[T]].toIterable[C]
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

sealed trait BaseCollectionValidator[E, C[_]] extends Validator[C[E]] {
  def elementValidator: Validator[E]
  def constraints: List[Constraint[C[E]]]
}

object BaseCollectionValidator {
  def unapply[E, C[_]](arg: BaseCollectionValidator[E, C]): Option[(Validator[E], List[Constraint[C[E]]])] = {
    Some(arg.elementValidator -> arg.constraints)
  }
}

case class CollectionValidator[E: Validator, C[_] <: Iterable[_]](elementValidator: Validator[E], constraints: List[Constraint[C[E]]])
    extends BaseCollectionValidator[E, C] {
  override def validate(t: C[E]): Boolean = {
    t.asInstanceOf[Iterable[E]].forall { e: E =>
      elementValidator.validate(e)
    } && constraints.forall(_.check(t))
  }
}

case class ArrayValidator[E: Validator, C[_] <: Array[_]](elementValidator: Validator[E], constraints: List[Constraint[C[E]]])
    extends BaseCollectionValidator[E, C] {
  override def validate(t: C[E]): Boolean = {
    t.asInstanceOf[Array[E]].forall { e: E =>
      elementValidator.validate(e)
    } && constraints.forall(_.check(t))
  }
}
