package sttp.tapir

import sttp.tapir.DecodeResult.Value

import scala.util.{Failure, Success, Try}

/**
  * A bi-directional mapping between values of type `L` and values of type `H`.
  *
  * Low-level values of type `L` can be **decoded** to a higher-level value of type `H`. The decoding can fail;
  * this is represented by a result of type [[DecodeResult.Failure]]. Failures might occur due to format errors, wrong
  * arity, exceptions, or validation errors. Validators can be added through the `validate` method.
  *
  * High-level values of type `H` can be **encoded** as a low-level value of type `L`.
  *
  * Mappings can be chained using one of the `map` functions.
  *
  * @tparam L The type of the low-level value.
  * @tparam H The type of the high-level value.
  */
trait Mapping[L, H] { outer =>
  def rawDecode(l: L): DecodeResult[H]
  def encode(h: H): L

  /**
    * - calls `rawDecode`
    * - catches any exceptions that might occur, converting them to decode failures
    * - validates the result
    */
  def decode(l: L): DecodeResult[H] = validate(tryRawDecode(l))

  private def tryRawDecode(l: L): DecodeResult[H] = {
    Try(rawDecode(l)) match {
      case Success(r) => r
      case Failure(e) => DecodeResult.Error(l.toString, e)
    }
  }

  private def validate(r: DecodeResult[H]): DecodeResult[H] = {
    r match {
      case DecodeResult.Value(v) =>
        val validationErrors = validator.validate(v)
        if (validationErrors.isEmpty) {
          DecodeResult.Value(v)
        } else {
          DecodeResult.InvalidValue(validationErrors)
        }
      case r => r
    }
  }

  def validator: Validator[H]
  // This is needed when combining multiple endpoint inputs/outputs, where unit is the neutral element.
  // When mapping *to* a unit (when H =:= Unit), the value won't be included in the tuple.
  def hIsUnit: Boolean

  def map[HH: IsUnit](codec: Mapping[H, HH]): Mapping[L, HH] = new Mapping[L, HH] {
    override def rawDecode(l: L): DecodeResult[HH] = outer.rawDecode(l).flatMap(codec.rawDecode)
    override def encode(hh: HH): L = outer.encode(codec.encode(hh))
    override def validator: Validator[HH] = outer.validator.contramap(codec.encode).and(codec.validator)
    override def hIsUnit: Boolean = implicitly[IsUnit[HH]].isUnit
  }

  def validate(v: Validator[H]): Mapping[L, H] = new Mapping[L, H] {
    override def rawDecode(l: L): DecodeResult[H] = outer.decode(l)
    override def encode(h: H): L = outer.encode(h)
    override def validator: Validator[H] = addEncodeToEnumValidator(v).and(outer.validator)
    override def hIsUnit: Boolean = outer.hIsUnit
  }

  private[tapir] def addEncodeToEnumValidator(v: Validator[H]): Validator[H] = {
    v match {
      case v @ Validator.Enum(_, None) => v.encode(encode)
      case _                           => v
    }
  }
}

object Mapping {
  def id[L: IsUnit]: Mapping[L, L] = new Mapping[L, L] {
    override def rawDecode(l: L): DecodeResult[L] = DecodeResult.Value(l)
    override def encode(h: L): L = h
    override def validator: Validator[L] = Validator.pass
    override def hIsUnit: Boolean = implicitly[IsUnit[L]].isUnit
  }
  def from[L, H: IsUnit](f: L => H)(g: H => L): Mapping[L, H] = fromDecode(f.andThen(Value(_)))(g)
  def fromDecode[L, H: IsUnit](f: L => DecodeResult[H])(g: H => L): Mapping[L, H] = new Mapping[L, H] {
    override def rawDecode(l: L): DecodeResult[H] = f(l)
    override def encode(h: H): L = g(h)
    override def validator: Validator[H] = Validator.pass
    override def hIsUnit: Boolean = implicitly[IsUnit[H]].isUnit
  }

  /**
    * A mapping which, during encoding, adds the given `prefix`. When decoding, the prefix is removed (if present),
    * otherwise an error is reported.
    */
  def stringPrefix(prefix: String): Mapping[String, String] = {
    val prefixLength = prefix.length
    def removePrefix(v: String): DecodeResult[String] =
      if (v.startsWith(prefix)) DecodeResult.Value(v.substring(prefixLength))
      else DecodeResult.Error(v, new IllegalArgumentException(s"The given value doesn't start with $prefix"))
    Mapping.fromDecode(removePrefix)(v => s"$prefix$v")
  }
}

trait IsUnit[T] {
  def isUnit: Boolean
}
object IsUnit {
  implicit val unitIsUnit: IsUnit[Unit] = new IsUnit[Unit] {
    override val isUnit: Boolean = true
  }
  implicit def otherIsNotUnit[T]: IsUnit[T] = new IsUnit[T] {
    override val isUnit: Boolean = false
  }
}
