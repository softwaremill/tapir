package sttp.tapir.macros

import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir.internal.{CodecEnumerationMacro, CodecValueClassMacro}
import sttp.tapir.Codec

trait CodecMacros {

  /** Creates a codec for an enumeration, where the validator is derived using [[sttp.tapir.Validator.derivedEnumeration]]. This requires
    * that all subtypes of the sealed hierarchy `T` must be `object` s.
    *
    * This method cannot be implicit, as there's no way to constraint the type `T` to be a sealed trait / class enumeration, so that this
    * would be invoked only when necessary.
    *
    * @tparam L
    *   The type of the low-level representation of the enum, typically a [[String]] or an [[Int]].
    * @tparam T
    *   The type of the enum.
    */
  def derivedEnumeration[L, T]: CreateDerivedEnumerationCodec[L, T] = macro CodecEnumerationMacro.derivedEnumeration[L, T]

  /** Creates a codec for an [[Enumeration]], where the validator is created using the enumeration's values. Unlike the default
    * [[derivedEnumerationValue]] method, which provides the schema implicitly, this variant allows customising how the codec is created.
    * This is useful if the low-level representation of the schema is different than a `String`, or if the enumeration's values should be
    * encoded in a different way than using `.toString`.
    *
    * Because of technical limitations of macros, the customisation arguments can't be given here directly, instead being delegated to
    * [[CreateDerivedEnumerationSchema]].
    *
    * @tparam L
    *   The type of the low-level representation of the enum, typically a [[String]] or an [[Int]].
    * @tparam T
    *   The type of the enum.
    */
  def derivedEnumerationValueCustomise[L, T <: scala.Enumeration#Value]: CreateDerivedEnumerationCodec[L, T] =
    macro CodecEnumerationMacro.derivedEnumerationValueCustomise[L, T]

  /** A default codec for enumerations, which returns a string-based enumeration codec, using the enum's `.toString` to encode values, and
    * performing a case-insensitive search through the possible values, converted to strings using `.toString`.
    *
    * To customise the enum encoding/decoding functions, provide a custom implicit created using [[derivedEnumerationValueCustomise]].
    */
  implicit def derivedEnumerationValue[T <: scala.Enumeration#Value]: Codec[String, T, TextPlain] =
    macro CodecEnumerationMacro.derivedEnumerationValue[T]

  /** Creates a codec for value class based on codecs defined in `Codec` companion */
  implicit def derivedValueClass[T <: AnyVal]: Codec[String, T, TextPlain] = macro CodecValueClassMacro.derivedValueClass[T]
}
