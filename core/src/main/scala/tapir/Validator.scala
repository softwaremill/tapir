package tapir

import tapir.generic.ValidateMagnoliaDerivation

trait Validator[T] { outer =>
  def validate(t: T): Boolean
  def unwrap: Validator[_] = outer
  def contramap[TT](g: TT => T): Validator[TT] = new Validator[TT] {
    override def validate(t: TT): Boolean = outer.validate(g(t))
    override def unwrap: Validator[_] = outer.unwrap
  }

  def forOption: Validator[Option[T]] = (t: Option[T]) => t.forall(outer.validate)

  def forSeq: Validator[Seq[T]] = (t: Seq[T]) => t.forall(outer.validate)

  def forVector: Validator[Vector[T]] = (t: Vector[T]) => t.forall(outer.validate)

  def forList: Validator[List[T]] = (t: List[T]) => t.forall(outer.validate)

  def forArray: Validator[Array[T]] = (t: Array[T]) => t.forall(outer.validate)

  def forSet: Validator[Set[T]] = (t: Set[T]) => t.forall(outer.validate)

  def forIterable[C[_] <: Iterable[T]]: Validator[C[T]] = (t: C[T]) => t.forall(outer.validate)
}

object Validator extends ValidateMagnoliaDerivation {
  def passing[T]: Validator[T] = (_: T) => true
  def rejecting[T]: Validator[T] = (_: T) => false

  implicit def validatorForOption[T: Validator]: Validator[Option[T]] = implicitly[Validator[T]].forOption
  implicit def validatorForArray[T: Validator]: Validator[Array[T]] = implicitly[Validator[T]].forArray
  implicit def validatorForIterable[T: Validator, C[_] <: Iterable[T]]: Validator[C[T]] = implicitly[Validator[T]].forIterable[C]
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
