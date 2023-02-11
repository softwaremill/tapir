package sttp.tapir.codec.enumeratum

import enumeratum._
import enumeratum.values._
import sttp.tapir.Schema.SName
import sttp.tapir._

trait TapirCodecEnumeratum {
  // Regular enums

  def validatorEnumEntry[E <: EnumEntry](implicit `enum`: Enum[E]): Validator.Enumeration[E] =
    Validator.enumeration(`enum`.values.toList, v => Some(v.entryName), Some(SName(fullName(`enum`))))

  implicit def schemaForEnumEntry[E <: EnumEntry](implicit annotations: SchemaAnnotations[E], `enum`: Enum[E]): Schema[E] =
    annotations.enrich(Schema[E](SchemaType.SString()).validate(validatorEnumEntry))

  def plainCodecEnumEntryUsing[E <: EnumEntry](
      f: String => Option[E]
  )(implicit `enum`: Enum[E]): Codec[String, E, CodecFormat.TextPlain] = {
    val validator = validatorEnumEntry
    Codec.string
      .mapDecode { s =>
        f(s)
          .map(DecodeResult.Value(_))
          .getOrElse(DecodeResult.InvalidValue(List(ValidationError(validator, s))))
      }(_.entryName)
      .validate(validatorEnumEntry)
  }

  def plainCodecEnumEntryDecodeCaseInsensitive[E <: EnumEntry](implicit `enum`: Enum[E]): Codec.PlainCodec[E] = plainCodecEnumEntryUsing(
    `enum`.withNameInsensitiveOption
  )

  implicit def plainCodecEnumEntry[E <: EnumEntry](implicit `enum`: Enum[E]): Codec.PlainCodec[E] = plainCodecEnumEntryUsing(
    `enum`.withNameOption
  )

  // Value enums

  def validatorValueEnumEntry[T, E <: ValueEnumEntry[T]](implicit `enum`: ValueEnum[T, E]): Validator[E] =
    Validator.enumeration(`enum`.values.toList, v => Some(v.value), Some(SName(fullName(`enum`))))

  implicit def schemaForIntEnumEntry[E <: IntEnumEntry](implicit annotations: SchemaAnnotations[E], `enum`: IntEnum[E]): Schema[E] =
    annotations.enrich(Schema[E](SchemaType.SInteger(), format = Some("int32")).validate(validatorValueEnumEntry[Int, E]))

  implicit def schemaForLongEnumEntry[E <: LongEnumEntry](implicit annotations: SchemaAnnotations[E], `enum`: LongEnum[E]): Schema[E] =
    annotations.enrich(Schema[E](SchemaType.SInteger(), format = Some("int64")).validate(validatorValueEnumEntry[Long, E]))

  implicit def schemaForShortEnumEntry[E <: ShortEnumEntry](implicit annotations: SchemaAnnotations[E], `enum`: ShortEnum[E]): Schema[E] =
    annotations.enrich(Schema[E](SchemaType.SInteger()).validate(validatorValueEnumEntry[Short, E]))

  implicit def schemaForStringEnumEntry[E <: StringEnumEntry](implicit
      annotations: SchemaAnnotations[E],
      `enum`: StringEnum[E]
  ): Schema[E] =
    annotations.enrich(Schema[E](SchemaType.SString()).validate(validatorValueEnumEntry[String, E]))

  implicit def schemaForByteEnumEntry[E <: ByteEnumEntry](implicit annotations: SchemaAnnotations[E], `enum`: ByteEnum[E]): Schema[E] =
    annotations.enrich(Schema[E](SchemaType.SInteger()).validate(validatorValueEnumEntry[Byte, E]))

  implicit def schemaForCharEnumEntry[E <: CharEnumEntry](implicit annotations: SchemaAnnotations[E], `enum`: CharEnum[E]): Schema[E] =
    annotations.enrich(Schema[E](SchemaType.SString()).validate(validatorValueEnumEntry[Char, E]))

  def plainCodecValueEnumEntry[T, E <: ValueEnumEntry[T]](implicit
      `enum`: ValueEnum[T, E],
      baseCodec: Codec.PlainCodec[T],
      schema: Schema[E]
  ): Codec.PlainCodec[E] =
    baseCodec
      .mapDecode { v =>
        `enum`
          .withValueOpt(v)
          .map(DecodeResult.Value(_))
          .getOrElse(DecodeResult.Mismatch(s"One of: ${`enum`.values.map(_.value).mkString(", ")}", v.toString))
      }(_.value)
      .schema(schema)

  implicit def plainCodecIntEnumEntry[E <: IntEnumEntry](implicit `enum`: IntEnum[E]): Codec.PlainCodec[E] =
    plainCodecValueEnumEntry[Int, E]

  implicit def plainCodecLongEnumEntry[E <: LongEnumEntry](implicit `enum`: LongEnum[E]): Codec.PlainCodec[E] =
    plainCodecValueEnumEntry[Long, E]

  implicit def plainCodecShortEnumEntry[E <: ShortEnumEntry](implicit `enum`: ShortEnum[E]): Codec.PlainCodec[E] =
    plainCodecValueEnumEntry[Short, E]

  implicit def plainCodecStringEnumEntry[E <: StringEnumEntry](implicit `enum`: StringEnum[E]): Codec.PlainCodec[E] =
    plainCodecValueEnumEntry[String, E]

  implicit def plainCodecByteEnumEntry[E <: ByteEnumEntry](implicit `enum`: ByteEnum[E]): Codec.PlainCodec[E] =
    plainCodecValueEnumEntry[Byte, E]

  private def fullName[T](t: T) = t.getClass.getName.replace("$", ".")

  // no Codec.PlainCodec[Char]
}
