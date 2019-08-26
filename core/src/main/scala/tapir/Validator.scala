package tapir

import tapir.generic.ValidateMagnoliaDerivation
import scala.collection.immutable

sealed trait Validator[T] {
  def validate(t: T): Boolean

  def contramap[TT](g: TT => T): Validator[TT] = Validator.Mapped(this, g)

  def asOptionElement: Validator[Option[T]] = Validator.CollectionElements(this, _.toIterable)
  def asArrayElements: Validator[Array[T]] = Validator.CollectionElements(this, _.toIterable)
  def asIterableElements[C[X] <: Iterable[X]]: Validator[C[T]] = Validator.CollectionElements(this, _.toIterable)

  def and(other: Validator[T]): Validator[T] = Validator.all(this, other)
  def or(other: Validator[T]): Validator[T] = Validator.any(this, other)
}

object Validator extends ValidateMagnoliaDerivation {
  def all[T](v: Validator[T]*): Validator[T] = if (v.size == 1) v.head else All[T](v.toList)
  def any[T](v: Validator[T]*): Validator[T] = if (v.size == 1) v.head else Any[T](v.toList)

  def pass[T]: Validator[T] = all()
  def reject[T]: Validator[T] = any()

  def min[T: Numeric](value: T): Validator[T] = Min(value)
  def pattern[T <: String](value: String): Validator[T] = Pattern(value)
  def minSize[T, C[_] <: Iterable[_]](value: Int): Validator[C[T]] = MinSize(value)
  def custom[T](doValidate: T => Boolean): Validator[T] = Custom(doValidate)

  //

  sealed trait Single[T] extends Validator[T]
  sealed trait Primitive[T] extends Single[T]

  case class Min[T](value: T)(implicit val valueIsNumeric: Numeric[T]) extends Primitive[T] {
    override def validate(actual: T): Boolean = implicitly[Numeric[T]].gteq(actual, value)
  }
  case class Max[T](value: T)(implicit val valueIsNumeric: Numeric[T]) extends Primitive[T] {
    override def validate(actual: T): Boolean = implicitly[Numeric[T]].lteq(actual, value)
  }
  case class Pattern[T <: String](value: String) extends Primitive[T] {
    override def validate(t: T): Boolean = t.matches(value)
  }
  case class MinSize[T, C[_] <: Iterable[_]](value: Int) extends Primitive[C[T]] {
    override def validate(t: C[T]): Boolean = t.size >= value
  }
  case class MaxSize[T, C[_] <: Iterable[_]](value: Int) extends Primitive[C[T]] {
    override def validate(t: C[T]): Boolean = t.size <= value
  }
  case class Custom[T](doValidate: T => Boolean) extends Primitive[T] {
    override def validate(t: T): Boolean = doValidate(t)
  }

  //

  case class CollectionElements[E, C[_]](
      elementValidator: Validator[E],
      toIterable: C[E] => Iterable[E]
  ) extends Single[C[E]] {
    override def validate(t: C[E]): Boolean = {
      toIterable(t).forall(elementValidator.validate)
    }
  }

  trait ProductField[T] {
    type FieldType
    def get(t: T): FieldType
    def validator: Validator[FieldType]
  }
  case class Product[T](fields: Map[String, ProductField[T]]) extends Single[T] {
    override def validate(t: T): Boolean = {
      fields.values.forall { f =>
        f.validator.validate(f.get(t))
      }
    }
  }

  case class OpenProduct[E](elementValidator: Validator[E]) extends Single[Map[String, E]] {
    override def validate(t: Map[String, E]): Boolean = {
      t.values.forall(elementValidator.validate)
    }
  }

  case class Mapped[TT, T](wrapped: Validator[T], g: TT => T) extends Single[TT] {
    override def validate(t: TT): Boolean = wrapped.validate(g(t))
  }

  //

  case class All[T](validators: immutable.Seq[Validator[T]]) extends Validator[T] {
    override def validate(t: T): Boolean = validators.forall(_.validate(t))
  }
  case class Any[T](validators: immutable.Seq[Validator[T]]) extends Validator[T] {
    override def validate(t: T): Boolean = validators.exists(_.validate(t))
  }

  //

  implicit def optionElement[T: Validator]: Validator[Option[T]] = implicitly[Validator[T]].asOptionElement
  implicit def arrayElements[T: Validator]: Validator[Array[T]] = implicitly[Validator[T]].asArrayElements
  implicit def iterableElements[T: Validator, C[X] <: Iterable[X]]: Validator[C[T]] = implicitly[Validator[T]].asIterableElements[C]
  implicit def openProduct[T: Validator]: Validator[Map[String, T]] = OpenProduct(implicitly[Validator[T]])
}
