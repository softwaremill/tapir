package sttp.tapir

import sttp.tapir.Schema.SName
import sttp.tapir.macros.ValidatorMacros

import scala.collection.immutable

sealed trait Validator[T] {
  def apply(t: T): List[ValidationError[_]]

  def contramap[TT](g: TT => T): Validator[TT] = Validator.Mapped(this, g)

  def and(other: Validator[T]): Validator[T] = Validator.all(this, other)
  def or(other: Validator[T]): Validator[T] = Validator.any(this, other)

  def show: Option[String] = Validator.show(this)
}

object Validator extends ValidatorMacros {

  // Used to capture encoding of a value to a raw format, which will then be directly rendered as a string in
  // documentation. This is needed as codecs for nested types aren't available.
  type EncodeToRaw[T] = T => Option[scala.Any]

  private val _pass: Validator[Nothing] = all()
  private val _reject: Validator[Nothing] = any()

  // -------------------------------------- SMART CONSTRUCTORS --------------------------------------
  def all[T](v: Validator[T]*): Validator[T] = if (v.size == 1) v.head else All[T](v.toList)

  def any[T](v: Validator[T]*): Validator[T] = if (v.size == 1) v.head else Any[T](v.toList)

  /** A validator instance that always pass. */
  def pass[T]: Validator[T] = _pass.asInstanceOf[Validator[T]]

  /** A validator instance that always reject. */
  def reject[T]: Validator[T] = _reject.asInstanceOf[Validator[T]]

  def min[T: Numeric](value: T, exclusive: Boolean = false): Validator.Primitive[T] = Min(value, exclusive)
  def max[T: Numeric](value: T, exclusive: Boolean = false): Validator.Primitive[T] = Max(value, exclusive)
  def positive[T: Numeric]: Validator.Primitive[T] = Min(implicitly[Numeric[T]].zero, exclusive = true)
  def positiveOrZero[T: Numeric]: Validator.Primitive[T] = Min(implicitly[Numeric[T]].zero, exclusive = false)
  def negative[T: Numeric]: Validator.Primitive[T] = Max(implicitly[Numeric[T]].zero, exclusive = true)
  def inRange[T: Numeric](min: T, max: T, minExclusive: Boolean = false, maxExclusive: Boolean = false): Validator[T] =
    Min(min, minExclusive).and(Max(max, maxExclusive))

  // string
  def pattern[T <: String](value: String): Validator.Primitive[T] = Pattern(value)

  /** Create a validator for minimum length constraints on strings.
    *
    * @param value
    *   The minimum allowed length.
    */
  def minLength[T <: String](value: Int): Validator.Primitive[T] = minLength(value, countCodePoints = false)

  /** Create a validator for minimum length constraints on strings.
    *
    * @param value
    *   The minimum allowed length.
    * @param countCodePoints
    *   A boolean parameter that determines whether the validation will consider code points or character count. When set to true, the
    *   validator will consider characters that are represented using two code units (a surrogate pair) in UTF-16 encoding allowing for a
    *   more accurate length validation for strings containing such characters (like emojis). Defaults to false, where length is validated
    *   based on the number of `Char` values in the string.
    */
  def minLength[T <: String](value: Int, countCodePoints: Boolean): Validator.Primitive[T] =
    MinLength(value, countCodePoints)

  /** Create a validator for maximum length constraints on strings.
    *
    * @param value
    *   The maximum allowed length.
    */
  def maxLength[T <: String](value: Int): Validator.Primitive[T] = maxLength(value, countCodePoints = false)

  /** Create a validator for maximum length constraints on strings.
    *
    * @param value
    *   The maximum allowed length.
    * @param countCodePoints
    *   A boolean parameter that determines whether the validation will consider code points or character count. When set to true, the
    *   validator will consider characters that are represented using two code units (a surrogate pair) in UTF-16 encoding allowing for a
    *   more accurate length validation for strings containing such characters (like emojis). Defaults to false, where length is validated
    *   based on the number of `Char` values in the string.
    */
  def maxLength[T <: String](value: Int, countCodePoints: Boolean): Validator.Primitive[T] =
    MaxLength(value, countCodePoints)

  /** Create a validator for fixed length constraints on strings.
    *
    * @param value
    *   The fixed allowed length.
    */
  def fixedLength[T <: String](value: Int): Validator[T] = fixedLength(value, countCodePoints = false)

  /** Create a validator for fixed length constraints on strings.
    *
    * @param value
    *   The fixed allowed length.
    * @param countCodePoints
    *   A boolean parameter that determines whether the validation will consider code points or character count. When set to true, the
    *   validator will consider characters that are represented using two code units (a surrogate pair) in UTF-16 encoding allowing for a
    *   more accurate length validation for strings containing such characters (like emojis). Defaults to false, where length is validated
    *   based on the number of `Char` values in the string.
    */
  def fixedLength[T <: String](value: Int, countCodePoints: Boolean): Validator[T] =
    MinLength(value, countCodePoints).and(MaxLength(value, countCodePoints))

  def nonEmptyString[T <: String]: Validator.Primitive[T] = MinLength(1)

  // iterable
  def minSize[T, C[_] <: Iterable[_]](value: Int): Validator.Primitive[C[T]] = MinSize(value)
  def maxSize[T, C[_] <: Iterable[_]](value: Int): Validator.Primitive[C[T]] = MaxSize(value)
  def nonEmpty[T, C[_] <: Iterable[_]]: Validator.Primitive[C[T]] = MinSize(1)
  def fixedSize[T, C[_] <: Iterable[_]](value: Int): Validator[C[T]] = MinSize(value).and(MaxSize(value))

  // enum
  /** Create an enumeration validator, with the given possible values.
    *
    * To represent the enumerated values in documentation, an encoding function needs to be provided. This can be done:
    *
    *   - by using the overloaded [[enumeration]] method with an `encode` parameter
    *   - by adding an encode function on an [[Validator.Enumeration]] instance using one of the `.encode` functions
    *   - by adding the validator directly to a codec (see [[Mapping.addEncodeToEnumValidator]])
    *   - when the values possible values are of a basic type (numbers, strings), the encode function is inferred if not present, when being
    *     added to the schema, see [[Schema.validate]]
    */
  def enumeration[T](possibleValues: List[T]): Validator.Enumeration[T] = Enumeration(possibleValues, None, None)

  /** Create an enumeration validator, with the given possible values, an optional encoding function (so that the enumerated values can be
    * represented in documentation), and an optional name (to create a reusable documentation component).
    *
    * @param encode
    *   Specify how values of this type can be encoded to a raw value, which will be used for documentation.
    */
  def enumeration[T](possibleValues: List[T], encode: EncodeToRaw[T], name: Option[SName] = None): Validator.Enumeration[T] =
    Enumeration(possibleValues, Some(encode), name)

  /** Create a custom validator.
    * @param validationLogic
    *   The logic of the validator
    * @param showMessage
    *   Description of the validator used when invoking [[Validator.show]].
    */
  def custom[T](validationLogic: T => ValidationResult, showMessage: Option[String] = None): Validator[T] =
    Custom(validationLogic, showMessage)

  // ---------- PRIMITIVE ----------
  sealed trait Primitive[T] extends Validator[T] {
    def doValidate(t: T): ValidationResult
    override def apply(t: T): List[ValidationError[T]] = doValidate(t) match {
      case ValidationResult.Valid => Nil
      case ValidationResult.Invalid(customMessages) =>
        customMessages match {
          case Nil => List(ValidationError(this, t, Nil, None))
          case l   => l.map(m => ValidationError(this, t, Nil, Some(m)))
        }
    }
  }
  case class Min[T](value: T, exclusive: Boolean)(implicit val valueIsNumeric: Numeric[T]) extends Primitive[T] {
    override def doValidate(t: T): ValidationResult =
      ValidationResult.validWhen(implicitly[Numeric[T]].gt(t, value) || (!exclusive && implicitly[Numeric[T]].equiv(t, value)))
  }
  case class Max[T](value: T, exclusive: Boolean)(implicit val valueIsNumeric: Numeric[T]) extends Primitive[T] {
    override def doValidate(t: T): ValidationResult =
      ValidationResult.validWhen(implicitly[Numeric[T]].lt(t, value) || (!exclusive && implicitly[Numeric[T]].equiv(t, value)))
  }
  case class Pattern[T <: String](value: String) extends Primitive[T] {
    override def doValidate(t: T): ValidationResult = ValidationResult.validWhen(t.matches(value))
  }

  case class MinLength[T <: String](value: Int, countCodePoints: Boolean) extends Primitive[T] {
    // The auxiliary constructor, custom copy methods, and the custom apply method in the companion object
    // have been introduced to maintain binary compatibility while adding a new field to the case class
    def this(value: Int) = this(value, false)
    def copy[TT <: String](value: Int): MinLength[TT] = MinLength[TT](value, false)
    def copy[TT <: String](value: Int = this.value, countCodePoints: Boolean = this.countCodePoints): MinLength[TT] =
      MinLength[TT](value, countCodePoints)

    override def doValidate(t: T): ValidationResult = {
      val size = if (countCodePoints) t.codePointCount(0, t.size) else t.size
      ValidationResult.validWhen(size >= value)
    }
  }
  object MinLength {
    def apply[T <: String](value: Int) = new MinLength[T](value, false)
  }

  case class MaxLength[T <: String](value: Int, countCodePoints: Boolean) extends Primitive[T] {
    // The auxiliary constructor, custom copy methods, and the custom apply method in the companion object
    // have been introduced to maintain binary compatibility while adding a new field to the case class
    def this(value: Int) = this(value, false)
    def copy[TT <: String](value: Int): MaxLength[TT] = MaxLength[TT](value, false)
    def copy[TT <: String](value: Int = this.value, countCodePoints: Boolean = this.countCodePoints): MaxLength[TT] =
      MaxLength[TT](value, countCodePoints)

    override def doValidate(t: T): ValidationResult = {
      val size = if (countCodePoints) t.codePointCount(0, t.size) else t.size
      ValidationResult.validWhen(size <= value)
    }
  }
  object MaxLength {
    def apply[T <: String](value: Int) = new MaxLength[T](value, false)
  }

  case class MinSize[T, C[_] <: Iterable[_]](value: Int) extends Primitive[C[T]] {
    override def doValidate(t: C[T]): ValidationResult = ValidationResult.validWhen(t.size >= value)
  }
  case class MaxSize[T, C[_] <: Iterable[_]](value: Int) extends Primitive[C[T]] {
    override def doValidate(t: C[T]): ValidationResult = ValidationResult.validWhen(t.size <= value)
  }
  case class Custom[T](validationLogic: T => ValidationResult, showMessage: Option[String] = None) extends Primitive[T] {
    override def doValidate(t: T): ValidationResult = validationLogic(t)
  }

  case class Enumeration[T](possibleValues: List[T], encode: Option[EncodeToRaw[T]], name: Option[SName]) extends Primitive[T] {
    override def doValidate(t: T): ValidationResult = ValidationResult.validWhen(possibleValues.contains(t))

    /** Specify how values of this type can be encoded to a raw value (typically a [[String]]). This encoding will be used when generating
      * documentation.
      */
    def encode(e: T => scala.Any): Enumeration[T] = copy(encode = Some(v => Some(e(v))))

    /** Specify that values of this type should be encoded to a raw value using an in-scope plain codec. This encoding will be used when
      * generating documentation.
      */
    def encodeWithPlainCodec(implicit c: Codec.PlainCodec[T]): Enumeration[T] = copy(encode = Some(v => Some(c.encode(v))))

    /** Specify that values of this type should be encoded to a raw value using an in-scope codec of the given format. This encoding will be
      * used when generating documentation.
      */
    def encodeWithCodec[CF <: CodecFormat](implicit c: Codec[String, T, CF]): Enumeration[T] = copy(encode = Some(v => Some(c.encode(v))))
  }

  //

  case class Mapped[TT, T](wrapped: Validator[T], g: TT => T) extends Validator[TT] {
    override def apply(t: TT): List[ValidationError[_]] = wrapped.apply(g(t))
  }

  case class All[T](validators: immutable.Seq[Validator[T]]) extends Validator[T] {
    override def apply(t: T): List[ValidationError[_]] = validators.flatMap(_.apply(t)).toList

    override def contramap[TT](g: TT => T): Validator[TT] = if (validators.isEmpty) this.asInstanceOf[Validator[TT]] else super.contramap(g)
    override def and(other: Validator[T]): Validator[T] = if (validators.isEmpty) other else All(validators :+ other)
  }

  case class Any[T](validators: immutable.Seq[Validator[T]]) extends Validator[T] {
    override def apply(t: T): List[ValidationError[_]] = {
      val results = validators.map(_.apply(t))
      if (results.exists(_.isEmpty)) {
        List.empty
      } else if (validators.nonEmpty) {
        results.flatten.toList
      } else {
        List(ValidationError[T](Any.PrimitiveRejectValidator, t))
      }
    }

    override def contramap[TT](g: TT => T): Validator[TT] = if (validators.isEmpty) Any(Nil) else super.contramap(g)
    override def or(other: Validator[T]): Validator[T] = if (validators.isEmpty) other else Any(validators :+ other)
  }
  object Any {
    private val _primitiveRejectValidator: Primitive[scala.Any] = Custom(_ => ValidationResult.Invalid("Validation rejected"))
    private[tapir] def PrimitiveRejectValidator[T]: Primitive[T] = _primitiveRejectValidator.asInstanceOf[Primitive[T]]
  }

  //

  def show[T](v: Validator[T]): Option[String] = {
    v match {
      case Min(value, exclusive)             => Some(s"${if (exclusive) ">" else ">="}$value")
      case Max(value, exclusive)             => Some(s"${if (exclusive) "<" else "<="}$value")
      case Pattern(value)                    => Some(s"~$value")
      case MinLength(value, _)               => Some(s"length>=$value")
      case MaxLength(value, _)               => Some(s"length<=$value")
      case MinSize(value)                    => Some(s"size>=$value")
      case MaxSize(value)                    => Some(s"size<=$value")
      case Custom(_, showMessage)            => showMessage.orElse(Some("custom"))
      case Enumeration(possibleValues, _, _) => Some(s"in(${possibleValues.mkString(",")}")
      case Mapped(wrapped, _)                => show(wrapped)
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

sealed trait ValidationResult
object ValidationResult {
  case object Valid extends ValidationResult
  case class Invalid(customMessage: List[String] = Nil) extends ValidationResult
  object Invalid {
    def apply(customMessage: String): Invalid = Invalid(List(customMessage))
    def apply(customMessage: String, customMessages: String*): Invalid = Invalid(customMessage :: customMessages.toList)
  }

  def validWhen(condition: Boolean): ValidationResult = if (condition) Valid else Invalid()
}

case class ValidationError[T](
    validator: Validator.Primitive[T],
    invalidValue: Any, // this isn't T, as we might want to report that a value can't be decoded as an enumeration member
    path: List[FieldName] = Nil,
    customMessage: Option[String] = None
) {
  def prependPath(f: FieldName): ValidationError[T] = copy(path = f :: path)
}
