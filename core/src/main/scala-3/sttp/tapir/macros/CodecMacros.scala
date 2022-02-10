package sttp.tapir.macros

import sttp.tapir.Codec.PlainCodec
import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir.{DecodeResult, Schema, Codec}
import sttp.tapir.internal.CodecValueClassMacro

trait CodecMacros {

  /** Creates a codec for an enumeration, where the validator is derived using [[sttp.tapir.Validator.derivedEnumeration]]. This requires
    * that all subtypes of the sealed hierarchy `T` must be `object`s.
    *
    * @tparam L
    *   The type of the low-level representation of the enum, typically a [[String]] or an [[Int]].
    * @tparam T
    *   The type of the enum.
    * @param decode
    *   How low-level values are decoded to the enum value. `None` if the low-level value is invalid, that is, when there's no high-level
    *   enum value.
    * @param encode
    *   How the enum value is encoded as a low-level value.
    */
  inline def derivedEnumeration[L, T](
      decode: L => Option[T],
      encode: T => L,
      default: Option[T] = None
  )(implicit baseCodec: PlainCodec[L]): PlainCodec[T] = {
    val s = Schema
      .derivedEnumeration[T](encode = Some(encode), schemaType = baseCodec.schema.schemaType.contramap(encode), default = default)

    baseCodec
      .mapDecode(s =>
        decode(s) match {
          case Some(value) => DecodeResult.Value(value)
          case None        => DecodeResult.Error(s.toString, new RuntimeException("Invalid enum value"))
        }
      )(encode)
      .schema(s)
  }

  /** Creates a codec for value class based on codecs defined in `Codec` companion */
  implicit inline def derivedValueClass[T <: AnyVal]: Codec[String, T, TextPlain] = CodecValueClassMacro.derivedValueClass[T]
}
