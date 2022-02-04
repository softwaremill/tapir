package sttp.tapir.macros

import sttp.tapir.Codec.PlainCodec
import sttp.tapir.internal.CodecEnumerationMacro
import sttp.tapir.{DecodeResult, Validator}

trait CodecMacros {

  /** Creates a codec for an enumeration, where the validator is derived using [[sttp.tapir.Validator.derivedEnumeration]]. This requires
    * that all subtypes of the sealed hierarchy `T` must be `object`s.
    *
    * @tparam L
    *   The type of the low-level representation of the enum, typically a [[String]] or an [[Int]].
    * @tparam T
    *   The type of the enum.
    *
    * Because of technical limitations of macros, the customisation arguments can't be given here directly, instead being delegated to
    * [[CreateDerivedEnumerationCodec]].
    */
  def derivedEnumeration[L, T]: CreateDerivedEnumerationCodec[L, T] = macro CodecEnumerationMacro.derivedEnumeration[L, T]
}

class CreateDerivedEnumerationCodec[L, T](validator: Validator.Enumeration[T]) {

  /** @param decode
    *   How low-level values are decoded to the enum value. `None` if the low-level value is invalid, that is, when there's no high-level
    *   enum value.
    * @param encode
    *   How the enum value is encoded as a low-level value.
    */
  def apply(
      decode: L => Option[T],
      encode: T => L,
      default: Option[T] = None
  )(implicit baseCodec: PlainCodec[L]): PlainCodec[T] = {
    val v = validator.encode(encode)

    val s0 = baseCodec.schema.map(decode)(encode).validate(v)
    val s = default.fold(s0)(d => s0.default(d, Some(encode(d))))

    baseCodec
      .mapDecode(s =>
        decode(s) match {
          case Some(value) => DecodeResult.Value(value)
          case None        => DecodeResult.Error(s.toString, new RuntimeException("Invalid enum value"))
        }
      )(encode)
      .schema(s)
  }
}
