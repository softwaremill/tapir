package tapir

import tapir.generic.internal.{ValidatorEnumMacro, ValidatorMagnoliaDerivation}

import scala.collection.immutable

sealed trait Validator[T] {
  def validate(t: T): List[ValidationError[_]]

  def contramap[TT](g: TT => T): Validator[TT] = Validator.Mapped(this, g)

  def asOptionElement: Validator[Option[T]] = Validator.CollectionElements(this, _.toIterable)
  def asArrayElements: Validator[Array[T]] = Validator.CollectionElements(this, _.toIterable)
  def asIterableElements[C[X] <: Iterable[X]]: Validator[C[T]] = Validator.CollectionElements(this, _.toIterable)

  def and(other: Validator[T]): Validator[T] = Validator.all(this, other)
  def or(other: Validator[T]): Validator[T] = Validator.any(this, other)

  def show: Option[String]
}

object Validator extends ValidatorMagnoliaDerivation with ValidatorEnumMacro {
  type EncodeToRaw[T] = T => Option[scala.Any]

  def all[T](v: Validator[T]*): Validator[T] = if (v.size == 1) v.head else All[T](v.toList)
  def any[T](v: Validator[T]*): Validator[T] = if (v.size == 1) v.head else Any[T](v.toList)

  def pass[T]: Validator[T] = all()
  def reject[T]: Validator[T] = any()

  def min[T: Numeric](value: T, exclusive: Boolean = false): Validator.Primitive[T] = Min(value, exclusive)
  def max[T: Numeric](value: T, exclusive: Boolean = false): Validator.Primitive[T] = Max(value, exclusive)
  def pattern[T <: String](value: String): Validator.Primitive[T] = Pattern(value)
  def minLength[T <: String](value: Int): Validator.Primitive[T] = MinLength(value)
  def maxLength[T <: String](value: Int): Validator.Primitive[T] = MaxLength(value)
  def minSize[T, C[_] <: Iterable[_]](value: Int): Validator.Primitive[C[T]] = MinSize(value)
  def maxSize[T, C[_] <: Iterable[_]](value: Int): Validator.Primitive[C[T]] = MaxSize(value)
  def custom[T](doValidate: T => Boolean, message: String): Validator.Primitive[T] = Custom(doValidate, message)

  /**
    * Creates an enum validator where all subtypes of the sealed hierarchy `T` are `object`s.
    * This enumeration will only be used for documentation, as a value outside of the allowed values will not be
    * decoded in the first place (the decoder has no other option than to fail).
    */
  def enum[T]: Validator.Enum[T] = macro validatorForEnum[T]
  def enum[T](possibleValues: List[T]): Validator.Enum[T] = Enum(possibleValues, None)

  /**
    * @param encode Specify how values of this type can be encoded to a raw value, which will be used for documentation.
    *               This will be automatically inferred if the validator is directly added to a codec.
    */
  def enum[T](possibleValues: List[T], encode: EncodeToRaw[T]): Validator.Enum[T] = Enum(possibleValues, Some(encode))
  //

  sealed trait Single[T] extends Validator[T]
  sealed trait Primitive[T] extends Single[T]

  case class Min[T](value: T, exclusive: Boolean)(implicit val valueIsNumeric: Numeric[T]) extends Primitive[T] {
    override def validate(t: T): List[ValidationError[_]] = {
      if (implicitly[Numeric[T]].gt(t, value) || (!exclusive && implicitly[Numeric[T]].equiv(t, value))) {
        List.empty
      } else {
        List(ValidationError(this, t))
      }
    }
    override def show: Option[String] = Some(s"${if (exclusive) ">" else ">="}$value")
  }
  case class Max[T](value: T, exclusive: Boolean)(implicit val valueIsNumeric: Numeric[T]) extends Primitive[T] {
    override def validate(t: T): List[ValidationError[_]] = {
      if (implicitly[Numeric[T]].lt(t, value) || (!exclusive && implicitly[Numeric[T]].equiv(t, value))) {
        List.empty
      } else {
        List(ValidationError(this, t))
      }
    }
    override def show: Option[String] = Some(s"${if (exclusive) "<" else "<="}$value")
  }
  case class Pattern[T <: String](value: String) extends Primitive[T] {
    override def validate(t: T): List[ValidationError[_]] = {
      if (t.matches(value)) {
        List.empty
      } else {
        List(ValidationError(this, t))
      }
    }
    override def show: Option[String] = Some(s"~$value")
  }
  case class MinLength[T <: String](value: Int) extends Primitive[T] {
    override def validate(t: T): List[ValidationError[_]] = {
      if (t.size >= value) {
        List.empty
      } else {
        List(ValidationError(this, t))
      }
    }
    override def show: Option[String] = Some(s"length>=$value")
  }
  case class MaxLength[T <: String](value: Int) extends Primitive[T] {
    override def validate(t: T): List[ValidationError[_]] = {
      if (t.size <= value) {
        List.empty
      } else {
        List(ValidationError(this, t))
      }
    }
    override def show: Option[String] = Some(s"length<=$value")
  }
  case class MinSize[T, C[_] <: Iterable[_]](value: Int) extends Primitive[C[T]] {
    override def validate(t: C[T]): List[ValidationError[_]] = {
      if (t.size >= value) {
        List.empty
      } else {
        List(ValidationError(this, t))
      }
    }
    override def show: Option[String] = Some(s"size>=$value")
  }
  case class MaxSize[T, C[_] <: Iterable[_]](value: Int) extends Primitive[C[T]] {
    override def validate(t: C[T]): List[ValidationError[_]] = {
      if (t.size <= value) {
        List.empty
      } else {
        List(ValidationError(this, t))
      }
    }
    override def show: Option[String] = Some(s"size<=$value")
  }
  case class Custom[T](doValidate: T => Boolean, message: String) extends Primitive[T] {
    override def validate(t: T): List[ValidationError[_]] = {
      if (doValidate(t)) {
        List.empty
      } else {
        List(ValidationError(this, t))
      }
    }
    override def show: Option[String] = Some(s"valid")
  }

  case class Enum[T](possibleValues: List[T], encode: Option[EncodeToRaw[T]]) extends Primitive[T] {
    override def validate(t: T): List[ValidationError[_]] = {
      if (possibleValues.contains(t)) {
        List.empty
      } else {
        List(ValidationError(this, t))
      }
    }
    override def show: Option[String] = Some(s"in(${possibleValues.mkString(",")}")

    /**
      * Specify how values of this type can be encoded to a raw value (typically a [[String]]). This encoding
      * will be used when generating documentation.
      */
    def encode(e: T => scala.Any): Enum[T] = copy(encode = Some(v => Some(e(v))))
  }

  //

  case class CollectionElements[E, C[_]](
      elementValidator: Validator[E],
      toIterable: C[E] => Iterable[E]
  ) extends Single[C[E]] {
    override def validate(t: C[E]): List[ValidationError[_]] = {
      toIterable(t).flatMap(elementValidator.validate).toList
    }
    override def show: Option[String] = elementValidator.show.map(se => s"elements($se)")
  }

  trait ProductField[T] {
    type FieldType
    def get(t: T): FieldType
    def validator: Validator[FieldType]
  }
  case class Product[T](fields: Map[String, ProductField[T]]) extends Single[T] {
    override def validate(t: T): List[ValidationError[_]] = {
      fields.values.flatMap { f =>
        f.validator.validate(f.get(t))
      }
    }.toList
    override def show: Option[String] =
      fields.flatMap {
        case (n, f) =>
          f.validator.show.map(n -> _)
      }.toList match {
        case Nil => None
        case l   => Some(l.map { case (n, s) => s"$n->($s)" }.mkString(","))
      }
  }

  case class OpenProduct[E](elementValidator: Validator[E]) extends Single[Map[String, E]] {
    override def validate(t: Map[String, E]): List[ValidationError[_]] = {
      t.values.flatMap(elementValidator.validate)
    }.toList
    override def show: Option[String] = elementValidator.show.map(se => s"elements($se)")
  }

  case class Mapped[TT, T](wrapped: Validator[T], g: TT => T) extends Single[TT] {
    override def validate(t: TT): List[ValidationError[_]] = wrapped.validate(g(t))
    override def show: Option[String] = wrapped.show
  }

  //

  case class All[T](validators: immutable.Seq[Validator[T]]) extends Validator[T] {
    override def validate(t: T): List[ValidationError[_]] = validators.flatMap(_.validate(t)).toList
    override def show: Option[String] = validators.flatMap(_.show) match {
      case immutable.Seq()  => None
      case immutable.Seq(s) => Some(s)
      case ss               => Some(s"all(${ss.mkString(",")})")
    }

    override def contramap[TT](g: TT => T): Validator[TT] = if (validators.isEmpty) All(Nil) else super.contramap(g)
    override def and(other: Validator[T]): Validator[T] = if (validators.isEmpty) other else All(validators :+ other)

    override def asArrayElements: Validator[Array[T]] = if (validators.isEmpty) All(Nil) else super.asArrayElements
    override def asIterableElements[C[X] <: Iterable[X]]: Validator[C[T]] =
      if (validators.isEmpty) All(Nil) else super.asIterableElements[C]
  }
  case class Any[T](validators: immutable.Seq[Validator[T]]) extends Validator[T] {
    override def validate(t: T): List[ValidationError[_]] = {
      val results = validators.map(_.validate(t))
      if (results.exists(_.isEmpty)) {
        List.empty
      } else {
        results.flatten.toList
      }
    }
    override def show: Option[String] = validators.flatMap(_.show) match {
      case immutable.Seq()  => Some("reject")
      case immutable.Seq(s) => Some(s)
      case ss               => Some(s"any(${ss.mkString(",")})")
    }

    override def contramap[TT](g: TT => T): Validator[TT] = if (validators.isEmpty) Any(Nil) else super.contramap(g)
    override def or(other: Validator[T]): Validator[T] = if (validators.isEmpty) other else Any(validators :+ other)
  }

  //

  implicit def optionElement[T: Validator]: Validator[Option[T]] = implicitly[Validator[T]].asOptionElement
  implicit def arrayElements[T: Validator]: Validator[Array[T]] = implicitly[Validator[T]].asArrayElements
  implicit def iterableElements[T: Validator, C[X] <: Iterable[X]]: Validator[C[T]] = implicitly[Validator[T]].asIterableElements[C]
  implicit def openProduct[T: Validator]: Validator[Map[String, T]] = OpenProduct(implicitly[Validator[T]])
}

case class ValidationError[T](validator: Validator.Primitive[T], invalidValue: T)
