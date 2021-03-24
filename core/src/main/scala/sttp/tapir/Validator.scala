package sttp.tapir

import sttp.tapir.generic.internal.ValidatorEnumMacro

import scala.collection.immutable

sealed trait Validator[T] {
  def apply(t: T): List[ValidationError[_]]

  def contramap[TT](g: TT => T): Validator[TT] = Validator.Mapped(this, g)

  def and(other: Validator[T]): Validator[T] = Validator.all(this, other)
  def or(other: Validator[T]): Validator[T] = Validator.any(this, other)

  def show: Option[String] = Validator.show(this)
}

object Validator extends ValidatorEnumMacro {
  // Used to capture encoding of a value to a raw format, which will then be directly rendered as a string in
  // documentation. This is needed as codecs for nested types aren't available.
  type EncodeToRaw[T] = T => Option[scala.Any]

  private val _pass: Validator[Nothing] = all()
  private val _reject: Validator[Nothing] = any()

  def all[T](v: Validator[T]*): Validator[T] = if (v.size == 1) v.head else All[T](v.toList)
  def any[T](v: Validator[T]*): Validator[T] = if (v.size == 1) v.head else Any[T](v.toList)

  /** A validator instance that always pass. */
  def pass[T]: Validator[T] = _pass.asInstanceOf[Validator[T]]

  /** A validator instance that always reject. */
  def reject[T]: Validator[T] = _reject.asInstanceOf[Validator[T]]

  def min[T: Numeric](value: T, exclusive: Boolean = false): Validator.Primitive[T] = Min(value, exclusive)
  def max[T: Numeric](value: T, exclusive: Boolean = false): Validator.Primitive[T] = Max(value, exclusive)
  def pattern[T <: String](value: String): Validator.Primitive[T] = Pattern(value)
  def minLength[T <: String](value: Int): Validator.Primitive[T] = MinLength(value)
  def maxLength[T <: String](value: Int): Validator.Primitive[T] = MaxLength(value)
  def minSize[T, C[_] <: Iterable[_]](value: Int): Validator.Primitive[C[T]] = MinSize(value)
  def maxSize[T, C[_] <: Iterable[_]](value: Int): Validator.Primitive[C[T]] = MaxSize(value)
  def custom[T](doValidate: T => List[ValidationError[_]], showMessage: Option[String] = None): Validator[T] =
    Custom(doValidate, showMessage)

  /** Creates an enum validator where all subtypes of the sealed hierarchy `T` are `object`s.
    * This enumeration will only be used for documentation, as a value outside of the allowed values will not be
    * decoded in the first place (the decoder has no other option than to fail).
    */
  def derivedEnum[T]: Validator.Enum[T] = macro validatorForEnum[T]
  def enum[T](possibleValues: List[T]): Validator.Enum[T] = Enum(possibleValues, None)

  /** @param encode Specify how values of this type can be encoded to a raw value, which will be used for documentation.
    *               This will be automatically inferred if the validator is directly added to a codec.
    */
  def enum[T](possibleValues: List[T], encode: EncodeToRaw[T]): Validator.Enum[T] = Enum(possibleValues, Some(encode))

  //

  sealed trait Single[T] extends Validator[T] // TODO: both needed?
  sealed trait Primitive[T] extends Single[T]

  case class Min[T](value: T, exclusive: Boolean)(implicit val valueIsNumeric: Numeric[T]) extends Primitive[T] {
    override def apply(t: T): List[ValidationError[_]] = {
      if (implicitly[Numeric[T]].gt(t, value) || (!exclusive && implicitly[Numeric[T]].equiv(t, value))) {
        List.empty
      } else {
        List(ValidationError.Primitive(this, t))
      }
    }
  }
  case class Max[T](value: T, exclusive: Boolean)(implicit val valueIsNumeric: Numeric[T]) extends Primitive[T] {
    override def apply(t: T): List[ValidationError[_]] = {
      if (implicitly[Numeric[T]].lt(t, value) || (!exclusive && implicitly[Numeric[T]].equiv(t, value))) {
        List.empty
      } else {
        List(ValidationError.Primitive(this, t))
      }
    }
  }
  case class Pattern[T <: String](value: String) extends Primitive[T] {
    override def apply(t: T): List[ValidationError[_]] = {
      if (t.matches(value)) {
        List.empty
      } else {
        List(ValidationError.Primitive(this, t))
      }
    }
  }
  case class MinLength[T <: String](value: Int) extends Primitive[T] {
    override def apply(t: T): List[ValidationError[_]] = {
      if (t.size >= value) {
        List.empty
      } else {
        List(ValidationError.Primitive(this, t))
      }
    }
  }
  case class MaxLength[T <: String](value: Int) extends Primitive[T] {
    override def apply(t: T): List[ValidationError[_]] = {
      if (t.size <= value) {
        List.empty
      } else {
        List(ValidationError.Primitive(this, t))
      }
    }
  }
  case class MinSize[T, C[_] <: Iterable[_]](value: Int) extends Primitive[C[T]] {
    override def apply(t: C[T]): List[ValidationError[_]] = {
      if (t.size >= value) {
        List.empty
      } else {
        List(ValidationError.Primitive(this, t))
      }
    }
  }
  case class MaxSize[T, C[_] <: Iterable[_]](value: Int) extends Primitive[C[T]] {
    override def apply(t: C[T]): List[ValidationError[_]] = {
      if (t.size <= value) {
        List.empty
      } else {
        List(ValidationError.Primitive(this, t))
      }
    }
  }
  case class Custom[T](doValidate: T => List[ValidationError[_]], showMessage: Option[String] = None) extends Validator.Single[T] {
    override def apply(t: T): List[ValidationError[_]] = {
      doValidate(t)
    }
  }

  case class Enum[T](possibleValues: List[T], encode: Option[EncodeToRaw[T]]) extends Primitive[T] {
    override def apply(t: T): List[ValidationError[_]] = {
      if (possibleValues.contains(t)) {
        List.empty
      } else {
        List(ValidationError.Primitive(this, t))
      }
    }

    /** Specify how values of this type can be encoded to a raw value (typically a [[String]]). This encoding
      * will be used when generating documentation.
      */
    def encode(e: T => scala.Any): Enum[T] = copy(encode = Some(v => Some(e(v))))
  }

  //

  case class Mapped[TT, T](wrapped: Validator[T], g: TT => T) extends Validator[TT] {
    override def apply(t: TT): List[ValidationError[_]] = wrapped.apply(g(t))
  }

  case class All[T](validators: immutable.Seq[Validator[T]]) extends Validator[T] {
    override def apply(t: T): List[ValidationError[_]] = validators.flatMap(_.apply(t)).toList

    override def contramap[TT](g: TT => T): Validator[TT] = if (validators.isEmpty) All(Nil) else super.contramap(g)
    override def and(other: Validator[T]): Validator[T] = if (validators.isEmpty) other else All(validators :+ other)
  }
  case class Any[T](validators: immutable.Seq[Validator[T]]) extends Validator[T] {
    override def apply(t: T): List[ValidationError[_]] = {
      val results = validators.map(_.apply(t))
      if (results.exists(_.isEmpty)) {
        List.empty
      } else {
        results.flatten.toList
      }
    }

    override def contramap[TT](g: TT => T): Validator[TT] = if (validators.isEmpty) Any(Nil) else super.contramap(g)
    override def or(other: Validator[T]): Validator[T] = if (validators.isEmpty) other else Any(validators :+ other)
  }

  //

  def show[T](v: Validator[T]): Option[String] = {
    v match {
      case Min(value, exclusive)   => Some(s"${if (exclusive) ">" else ">="}$value")
      case Max(value, exclusive)   => Some(s"${if (exclusive) "<" else "<="}$value")
      case Pattern(value)          => Some(s"~$value")
      case MinLength(value)        => Some(s"length>=$value")
      case MaxLength(value)        => Some(s"length<=$value")
      case MinSize(value)          => Some(s"size>=$value")
      case MaxSize(value)          => Some(s"size<=$value")
      case Custom(_, showMessage)  => showMessage.orElse(Some("custom"))
      case Enum(possibleValues, _) => Some(s"in(${possibleValues.mkString(",")}")
      case Mapped(wrapped, _)      => show(wrapped)
      case All(validators) =>
        validators.flatMap(show(_)) match {
          case immutable.Seq()  => None
          case immutable.Seq(s) => Some(s)
          case ss               => Some(s"all(${ss.mkString(",")})")
        }
      case Any(validators) =>
        validators.flatMap(show(_)) match {
          case immutable.Seq()  => Some("reject")
          case immutable.Seq(s) => Some(s)
          case ss               => Some(s"any(${ss.mkString(",")})")
        }
    }
  }
}

sealed trait ValidationError[T] {
  def prependPath(f: FieldName): ValidationError[T]
  def invalidValue: T
  def path: List[FieldName]
}

object ValidationError {
  case class Primitive[T](validator: Validator.Primitive[T], invalidValue: T, path: List[FieldName] = Nil) extends ValidationError[T] {
    override def prependPath(f: FieldName): ValidationError[T] = copy(path = f :: path)
  }

  case class Custom[T](invalidValue: T, message: String, path: List[FieldName] = Nil) extends ValidationError[T] {
    override def prependPath(f: FieldName): ValidationError[T] = copy(path = f :: path)
  }
}
