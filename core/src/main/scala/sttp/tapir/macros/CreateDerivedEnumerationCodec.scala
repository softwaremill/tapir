package sttp.tapir.macros

import sttp.tapir.Codec.PlainCodec
import sttp.tapir.{Codec, DecodeResult, SchemaAnnotations, ValidationError, Validator}

class CreateDerivedEnumerationCodec[L, T](validator: Validator.Enumeration[T], schemaAnnotations: SchemaAnnotations[T]) {

  // needed for binary compatibility
  def this(validator: Validator.Enumeration[T]) = this(validator, SchemaAnnotations.empty)

  /** Creates the codec using the provided decoding & encoding functions.
    *
    * @param decode
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
          case None        => DecodeResult.InvalidValue(List(ValidationError(v, s)))
        }
      )(encode)
      .schema(schemaAnnotations.enrich(s))
  }

  /** Creates the codec assuming the low-level representation is a `String`. The encoding function uses the enum's `.toString`. Similarly,
    * the decoding function performs a case-insensitive search using the enum's `.toString`
    */
  def defaultStringBased(implicit lIsString: L =:= String): PlainCodec[T] =
    apply(
      s => validator.possibleValues.find(_.toString.equalsIgnoreCase(s.asInstanceOf[String])), // we know that L == String
      _.toString.asInstanceOf[L],
      None
    )(Codec.string.asInstanceOf[PlainCodec[L]])
}
