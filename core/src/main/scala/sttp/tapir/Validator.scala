package sttp.tapir

import sttp.tapir.Schema.SName
import sttp.tapir.macros.ValidatorMacros
import sttp.tapir.ValidationResult.{Invalid, Valid}

import scala.collection.immutable

sealed trait Validator[T] {

  def apply(t: T): ValidationResult[T]

  def contramap[TT](g: TT => T): Validator[TT] = Validator.Mapped(this, g)

  def and(other: Validator[T]): Validator[T] = Validator.all(this, other)

  def or(other: Validator[T]): Validator[T] = Validator.any(this, other)

  def show: Option[String]
}
object Validator extends ValidatorMacros {

  sealed trait Primitive[T] extends Validator[T]

  // Used to capture encoding of a value to a raw format, which will then be directly rendered as a string in
  // documentation. This is needed as codecs for nested types aren't available.
  type EncodeToRaw[T] = T => Option[scala.Any]

  private val _pass: Validator[Nothing] = all()
  private val _reject: Validator[Nothing] = any()

  //-------------------------------------- SMART CONSTRUCTORS --------------------------------------

  def all[T](v: Validator[T]*): Validator[T] = if (v.size == 1) v.head else All[T](v.toList)

  def any[T](v: Validator[T]*): Validator[T] = if (v.size == 1) v.head else Any[T](v.toList)

  /** A validator instance that always pass. */
  def pass[T]: Validator[T] = _pass.asInstanceOf[Validator[T]]

  /** A validator instance that always reject. */
  def reject[T]: Validator[T] = _reject.asInstanceOf[Validator[T]]

  /** Create a custom validator */
  def custom[T](doValidate: T => ValidationResult[T], showMessage: Option[String] = None): Validator[T] =
    Custom(doValidate, showMessage)

  //numeric
  def min[T: Numeric](value: T, exclusive: Boolean = false): Validator.Primitive[T] = Min(value, exclusive)
  def max[T: Numeric](value: T, exclusive: Boolean = false): Validator[T] = Max(value, exclusive)
  def positive[T: Numeric]: Validator[T] = Min(implicitly[Numeric[T]].zero, exclusive = false)
  def negative[T: Numeric]: Validator[T] = Max(implicitly[Numeric[T]].zero, exclusive = true)
  def inRange[T: Numeric](min: T, max: T, minExclusive: Boolean = false, maxExclusive: Boolean = false): Validator[T] =
    Min(min, minExclusive).and(Max(max, maxExclusive))

  //string
  def pattern[T <: String](value: String): Validator.Primitive[T] = Pattern(value)
  def minLength[T <: String](value: Int): Validator.Primitive[T] = MinLength(value)
  def maxLength[T <: String](value: Int): Validator.Primitive[T] = MaxLength(value)
  def fixedLength[T <: String](value: Int): Validator[T] = MinLength(value).and(MaxLength(value))
  def nonEmptyString[T <: String]: Validator.Primitive[T] = MinLength(0)

  //iterable
  def minSize[T, C[_] <: Iterable[_]](value: Int): Validator.Primitive[C[T]] = MinSize(value)
  def maxSize[T, C[_] <: Iterable[_]](value: Int): Validator.Primitive[C[T]] = MaxSize(value)
  def nonEmpty[T, C[_] <: Iterable[_]]: Validator.Primitive[C[T]] = MinSize(0)
  def fixedSize[T, C[_] <: Iterable[_]](value: Int): Validator[C[T]] = MinSize(value).and(MaxSize(value))

  //enum
  /** Create an enumeration validator, with the given possible values.
    *
    * To represent the enumerated values in documentation, an encoding function needs to be provided. This can be done: * by using the
    * overloaded [[enumeration]] method with an `encode` parameter * by adding an encode function on an [[Validator.Enumeration]] instance
    * using one of the `.encode` functions * by adding the validator directly to a codec (see [[Mapping.addEncodeToEnumValidator]] * when
    * the values possible values are of a basic type (numbers, strings), the encode function is inferred if not present, when being added to
    * the schema, see [[Schema.validate]]
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

  //---------------------------- PRIMITIVE ----------------------------
  case class Min[T](value: T, exclusive: Boolean)(implicit val valueIsNumeric: Numeric[T]) extends Primitive[T] {
    override def show: Option[String] = Some(s"${if (exclusive) ">" else ">="}$value")
    override def apply(t: T): ValidationResult[T] =
      ValidationResult.when(valueIsNumeric.gt(t, value) || (!exclusive && valueIsNumeric.equiv(t, value)))(
        ifTrue = Valid(t),
        ifFalse = Invalid(
          value = t,
          errors = List(ValidationError.expectedTo(s"be greater than ${if (exclusive) "" else "or equal to "}$value", butWas = t))
        )
      )
  }
  case class Max[T](value: T, exclusive: Boolean)(implicit val valueIsNumeric: Numeric[T]) extends Primitive[T] {
    override def show: Option[String] = Some(s"${if (exclusive) "<" else "<="}$value")
    override def apply(t: T): ValidationResult[T] =
      ValidationResult.when(valueIsNumeric.lt(t, value) || (!exclusive && valueIsNumeric.equiv(t, value)))(
        ifTrue = Valid(t),
        ifFalse = Invalid(
          value = t,
          errors = List(ValidationError.expectedTo(s"be less than ${if (exclusive) "" else "or equal to "}$value", butWas = t))
        )
      )
  }

  case class Pattern[T <: String](value: String) extends Primitive[T] {
    override def show: Option[String] = Some(s"~$value")
    override def apply(t: T): ValidationResult[T] =
      ValidationResult.when(t.matches(value))(
        ifTrue = Valid(t),
        ifFalse = Invalid(
          value = t,
          errors = List(ValidationError.expectedTo(s"match '$value'", butWas = t))
        )
      )
  }

  case class MinLength[T <: String](value: Int) extends Primitive[T] {
    override def show: Option[String] = Some(s"length>=$value")
    override def apply(t: T): ValidationResult[T] =
      ValidationResult.when(t.size >= value)(
        ifTrue = Valid(t),
        ifFalse = Invalid(
          value = t,
          errors = List(ValidationError.expectedTo(s"have length greater than or equal to $value", butWas = t))
        )
      )
  }

  case class MaxLength[T <: String](value: Int) extends Primitive[T] {
    override def show: Option[String] = Some(s"length<=$value")
    override def apply(t: T): ValidationResult[T] =
      ValidationResult.when(t.size <= value)(
        ifTrue = Valid(t),
        ifFalse = Invalid(
          value = t,
          errors = List(ValidationError.expectedTo(s"have length less than or equal to $value", butWas = t))
        )
      )
  }

  case class MinSize[T, C[_] <: Iterable[_]](value: Int) extends Primitive[C[T]] {
    override def show: Option[String] = Some(s"size>=$value")
    override def apply(t: C[T]): ValidationResult[C[T]] =
      ValidationResult.when(t.size >= value)(
        ifTrue = Valid(t),
        ifFalse = Invalid(
          value = t,
          errors = List(ValidationError.expected(path => s"size of $path")(to = s"be greater than or equal to $value", butWas = t))
        )
      )
  }

  case class MaxSize[T, C[_] <: Iterable[_]](value: Int) extends Primitive[C[T]] {
    override def show: Option[String] = Some(s"size<=$value")
    override def apply(t: C[T]): ValidationResult[C[T]] =
      ValidationResult.when(t.size <= value)(
        ifTrue = Valid(t),
        ifFalse = Invalid(
          value = t,
          errors = List(ValidationError.expected(path => s"size of $path")(to = s"be less than or equal to $value", butWas = t.size))
        )
      )
  }

  case class Enumeration[T](possibleValues: List[T], encode: Option[EncodeToRaw[T]], name: Option[SName]) extends Primitive[T] {

    override def show: Option[String] = Some(s"in(${possibleValues.mkString(",")}")

    override def apply(t: T): ValidationResult[T] =
      ValidationResult.when(possibleValues.contains(t))(
        ifTrue = Valid(t),
        ifFalse = Invalid(
          value = t,
          errors = List(ValidationError.expectedTo(s"be within $possibleValues", butWas = t))
        )
      )

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

  //---------------------------- VALIDATOR ----------------------------
  case class Custom[T](doValidate: T => ValidationResult[T], showMessage: Option[String] = None) extends Validator[T] {
    override def show: Option[String] = showMessage.orElse(Some("custom"))
    override def apply(t: T): ValidationResult[T] = doValidate(t)
  }
  object Custom {
    def apply[T](showMessage: String)(doValidate: T => ValidationResult[T]): Custom[T] =
      Custom[T](doValidate, Option(showMessage))
  }
  case class Mapped[TT, T](wrapped: Validator[T], g: TT => T) extends Validator[TT] {
    override def show: Option[String] = wrapped.show
    override def apply(t: TT): ValidationResult[TT] = wrapped.apply(g(t)) match {
      case Valid(_)           => Valid(t)
      case Invalid(_, errors) => Invalid(t, errors)
    }
  }
  case class All[T](validators: immutable.Seq[Validator[T]]) extends Validator[T] {

    override def show: Option[String] = validators.flatMap(_.show) match {
      case Nil      => None
      case x :: Nil => Some(x)
      case xs       => Some(s"all(${xs.mkString(",")})")
    }

    override def apply(t: T): ValidationResult[T] = {
      ValidationResult.partition(
        validators.toList.map(_.apply(t))
      ) match {
        case (Nil, _)     => ValidationResult.Valid(t)
        case (invalid, _) => ValidationResult.Invalid(t, invalid.flatMap(_.errors))
      }
    }

    override def contramap[TT](g: TT => T): Validator[TT] =
      if (validators.isEmpty) All(Nil) else super.contramap(g)

    override def and(other: Validator[T]): Validator[T] =
      if (validators.isEmpty) other else All(validators :+ other)
  }
  case class Any[T](validators: immutable.Seq[Validator[T]]) extends Validator[T] {

    override def show: Option[String] = validators.flatMap(_.show) match {
      case Nil      => Some("reject")
      case x :: Nil => Some(x)
      case xs       => Some(s"any(${xs.mkString(",")})")
    }

    override def apply(t: T): ValidationResult[T] =
      ValidationResult.partition(
        validators.toList.map(_.apply(t))
      ) match {
        case (_, _ :: _)    => ValidationResult.Valid(t)
        case (invalid, Nil) => ValidationResult.Invalid(t, invalid.flatMap(_.errors))
      }

    override def contramap[TT](g: TT => T): Validator[TT] =
      if (validators.isEmpty) Any(Nil) else super.contramap(g)

    override def or(other: Validator[T]): Validator[T] =
      if (validators.isEmpty) other else Any(validators :+ other)
  }
}

sealed trait ValidationResult[+T] {
  def valid[TT >: T]: Option[Valid[TT]] =
    this match {
      case v: Valid[TT]   => Some(v)
      case _: Invalid[TT] => None
    }

  def invalid[TT >: T]: Option[Invalid[TT]] =
    this match {
      case _: Valid[TT]   => None
      case i: Invalid[TT] => Some(i)
    }
}
object ValidationResult {
  case class Valid[T](value: T) extends ValidationResult[T]
  case class Invalid[T](value: T, errors: List[ValidationError]) extends ValidationResult[T] {

    /** Messages describing a list of validation errors: which values are invalid, and why. */
    def allMessages: List[ValidationMessage] =
      errors.map(_.message)

    /** Message describing a list of validation errors: which values are invalid, and why. */
    def description: String =
      allMessages.map(_.value).mkString(",")
  }

  def partition[A](xs: List[ValidationResult[A]]): (List[Invalid[A]], List[Valid[A]]) = {
    val l = List.newBuilder[Invalid[A]]
    val r = List.newBuilder[Valid[A]]

    xs.foreach { x =>
      (x match {
        case i: Invalid[A] => Left(i)
        case v: Valid[A]   => Right(v)
      }) match {
        case Left(x1)  => l += x1
        case Right(x2) => r += x2
      }
    }
    (l.result(), r.result())
  }

  def when[T](c: => Boolean)(ifTrue: => ValidationResult[T], ifFalse: => ValidationResult[T]): ValidationResult[T] =
    if (c) ifTrue else ifFalse
}

case class ValidationMessage(value: String) extends AnyVal
case class ValidationError(f: FieldPath => String, path: FieldNames = FieldNames.empty) {

  def prependPath(f: FieldName): ValidationError =
    copy(path = path.prependPath(f))

  def message: ValidationMessage = ValidationMessage(f(path.asPath.getOrElse(FieldPath("value"))))

  override def toString: String = message.value
}
object ValidationError {

  def expectedTo[T](to: String, butWas: T): ValidationError =
    expected(_.value)(to, butWas)

  def expected[T](subject: FieldPath => String)(to: String, butWas: T): ValidationError =
    ValidationError(path => s"expected ${subject(path)} to $to, but was $butWas")
}
