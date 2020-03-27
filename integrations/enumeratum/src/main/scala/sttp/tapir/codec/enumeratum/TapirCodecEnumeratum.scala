package sttp.tapir.codec.enumeratum

import enumeratum._
import enumeratum.values._
import sttp.tapir._

trait TapirCodecEnumeratum {
  // Regular enums

  implicit def validatorEnumEntry[E <: EnumEntry](implicit enum: Enum[E]): Validator[E] =
    Validator.enum(enum.values.toList, v => Some(v.entryName))

  implicit def schemaForEnumEntry[E <: EnumEntry]: Schema[E] =
    Schema(SchemaType.SString)

  implicit def plainCodecEnumEntry[E <: EnumEntry](implicit enum: Enum[E]): Codec.PlainCodec[E] =
    Codec.stringPlainCodecUtf8
      .mapDecode { s =>
        enum
          .withNameOption(s)
          .map(DecodeResult.Value(_))
          .getOrElse(DecodeResult.Mismatch(s"One of: ${enum.values.map(_.entryName).mkString(", ")}", s))
      }(_.entryName)
      .validate(validatorEnumEntry(enum))

  // Value enums

  def validatorValueEnumEntry[T, E <: ValueEnumEntry[T]](implicit enum: ValueEnum[T, E]): Validator[E] =
    Validator.enum(enum.values.toList, v => Some(v.value))

  implicit def validatorIntEnumEntry[E <: IntEnumEntry](implicit enum: IntEnum[E]): Validator[E] =
    validatorValueEnumEntry[Int, E]

  implicit def validatorLongEnumEntry[E <: LongEnumEntry](implicit enum: LongEnum[E]): Validator[E] =
    validatorValueEnumEntry[Long, E]

  implicit def validatorShortEnumEntry[E <: ShortEnumEntry](implicit enum: ShortEnum[E]): Validator[E] =
    validatorValueEnumEntry[Short, E]

  implicit def validatorStringEnumEntry[E <: StringEnumEntry](implicit enum: StringEnum[E]): Validator[E] =
    validatorValueEnumEntry[String, E]

  implicit def validatorByteEnumEntry[E <: ByteEnumEntry](implicit enum: ByteEnum[E]): Validator[E] =
    validatorValueEnumEntry[Byte, E]

  implicit def validatorCharEnumEntry[E <: CharEnumEntry](implicit enum: CharEnum[E]): Validator[E] =
    validatorValueEnumEntry[Char, E]

  implicit def schemaForIntEnumEntry[E <: IntEnumEntry]: Schema[E] =
    Schema(SchemaType.SInteger)

  implicit def schemaForLongEnumEntry[E <: LongEnumEntry]: Schema[E] =
    Schema(SchemaType.SInteger)

  implicit def schemaForShortEnumEntry[E <: ShortEnumEntry]: Schema[E] =
    Schema(SchemaType.SInteger)

  implicit def schemaForStringEnumEntry[E <: StringEnumEntry]: Schema[E] =
    Schema(SchemaType.SString)

  implicit def schemaForByteEnumEntry[E <: ByteEnumEntry]: Schema[E] =
    Schema(SchemaType.SInteger)

  implicit def schemaForCharEnumEntry[E <: CharEnumEntry]: Schema[E] =
    Schema(SchemaType.SString)

  def plainCodecValueEnumEntry[T, E <: ValueEnumEntry[T]](
      implicit enum: ValueEnum[T, E],
      baseCodec: Codec.PlainCodec[T],
      validator: Validator[E]
  ): Codec.PlainCodec[E] =
    baseCodec
      .mapDecode { v =>
        enum
          .withValueOpt(v)
          .map(DecodeResult.Value(_))
          .getOrElse(DecodeResult.Mismatch(s"One of: ${enum.values.map(_.value).mkString(", ")}", v.toString))
      }(_.value)
      .validate(validator)

  implicit def plainCodecIntEnumEntry[E <: IntEnumEntry](implicit enum: IntEnum[E]): Codec.PlainCodec[E] =
    plainCodecValueEnumEntry[Int, E]

  implicit def plainCodecLongEnumEntry[E <: LongEnumEntry](implicit enum: LongEnum[E]): Codec.PlainCodec[E] =
    plainCodecValueEnumEntry[Long, E]

  implicit def plainCodecShortEnumEntry[E <: ShortEnumEntry](implicit enum: ShortEnum[E]): Codec.PlainCodec[E] =
    plainCodecValueEnumEntry[Short, E]

  implicit def plainCodecStringEnumEntry[E <: StringEnumEntry](implicit enum: StringEnum[E]): Codec.PlainCodec[E] =
    plainCodecValueEnumEntry[String, E]

  implicit def plainCodecByteEnumEntry[E <: ByteEnumEntry](implicit enum: ByteEnum[E]): Codec.PlainCodec[E] =
    plainCodecValueEnumEntry[Byte, E]

  // no Codec.PlainCodec[Char]
}
