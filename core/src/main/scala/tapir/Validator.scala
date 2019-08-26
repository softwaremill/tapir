package tapir

import tapir.generic.ValidateMagnoliaDerivation
import scala.collection.immutable

sealed trait Validator[T] {
  def validate(t: T): List[ValidationError]

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
  def max[T: Numeric](value: T): Validator[T] = Max(value)
  def pattern[T <: String](value: String): Validator[T] = Pattern(value)
  def minSize[T, C[_] <: Iterable[_]](value: Int): Validator[C[T]] = MinSize(value)
  def maxSize[T, C[_] <: Iterable[_]](value: Int): Validator[C[T]] = MaxSize(value)
  def custom[T](doValidate: T => Boolean, message: String): Validator[T] = Custom(doValidate, message)

  //

  sealed trait Single[T] extends Validator[T]
  sealed trait Primitive[T] extends Single[T]

  case class Min[T](value: T)(implicit val valueIsNumeric: Numeric[T]) extends Primitive[T] {
    override def validate(actual: T): List[ValidationError] = {
      if (implicitly[Numeric[T]].gteq(actual, value)) {
        List.empty
      } else {
        List(ValidationError(s"Expected $actual to be greater than or equal to $value"))
      }
    }
  }
  case class Max[T](value: T)(implicit val valueIsNumeric: Numeric[T]) extends Primitive[T] {
    override def validate(actual: T): List[ValidationError] = {
      if (implicitly[Numeric[T]].lteq(actual, value)) {
        List.empty
      } else {
        List(ValidationError(s"Expected $actual to be lower than or equal to $value"))
      }
    }
  }
  case class Pattern[T <: String](value: String) extends Primitive[T] {
    override def validate(t: T): List[ValidationError] = {
      if (t.matches(value)) {
        List.empty
      } else {
        List(ValidationError(s"Expected '$t' to match '$value'"))
      }
    }
  }
  case class MinSize[T, C[_] <: Iterable[_]](value: Int) extends Primitive[C[T]] {
    override def validate(t: C[T]): List[ValidationError] = {
      if (t.size >= value) {
        List.empty
      } else {
        List(ValidationError(s"Expected collection size(${t.size}) to be greater or equal to $value"))
      }
    }
  }
  case class MaxSize[T, C[_] <: Iterable[_]](value: Int) extends Primitive[C[T]] {
    override def validate(t: C[T]): List[ValidationError] = {
      if (t.size <= value) {
        List.empty
      } else {
        List(ValidationError(s"Expected collection size(${t.size}) to be lower or equal to $value"))
      }
    }
  }
  case class Custom[T](doValidate: T => Boolean, message: String) extends Primitive[T] {
    override def validate(t: T): List[ValidationError] = {
      if (doValidate(t)) {
        List.empty
      } else {
        List(ValidationError(s"Expected $t to pass custom validation: $message"))
      }
    }
  }

  //

  case class CollectionElements[E, C[_]](
      elementValidator: Validator[E],
      toIterable: C[E] => Iterable[E]
  ) extends Single[C[E]] {
    override def validate(t: C[E]): List[ValidationError] = {
      toIterable(t).flatMap(elementValidator.validate).toList
    }
  }

  trait ProductField[T] {
    type FieldType
    def get(t: T): FieldType
    def validator: Validator[FieldType]
  }
  case class Product[T](fields: Map[String, ProductField[T]]) extends Single[T] {
    override def validate(t: T): List[ValidationError] = {
      fields.values.flatMap { f =>
        f.validator.validate(f.get(t))
      }
    }.toList
  }

  case class OpenProduct[E](elementValidator: Validator[E]) extends Single[Map[String, E]] {
    override def validate(t: Map[String, E]): List[ValidationError] = {
      t.values.flatMap(elementValidator.validate)
    }.toList
  }

  case class Mapped[TT, T](wrapped: Validator[T], g: TT => T) extends Single[TT] {
    override def validate(t: TT): List[ValidationError] = wrapped.validate(g(t))
  }

  //

  case class All[T](validators: immutable.Seq[Validator[T]]) extends Validator[T] {
    override def validate(t: T): List[ValidationError] = validators.flatMap(_.validate(t)).toList
  }
  case class Any[T](validators: immutable.Seq[Validator[T]]) extends Validator[T] {
    override def validate(t: T): List[ValidationError] = {
      val results = validators.map(_.validate(t))
      if (results.exists(_.isEmpty)) {
        List.empty
      } else {
        results.flatten.toList
      }
    }
  }

  //

  implicit def optionElement[T: Validator]: Validator[Option[T]] = implicitly[Validator[T]].asOptionElement
  implicit def arrayElements[T: Validator]: Validator[Array[T]] = implicitly[Validator[T]].asArrayElements
  implicit def iterableElements[T: Validator, C[X] <: Iterable[X]]: Validator[C[T]] = implicitly[Validator[T]].asIterableElements[C]
  implicit def openProduct[T: Validator]: Validator[Map[String, T]] = OpenProduct(implicitly[Validator[T]])
}

case class ValidationError(message: String)
