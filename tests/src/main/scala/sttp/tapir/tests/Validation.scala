package sttp.tapir.tests

import com.softwaremill.tagging.{@@, Tagger}
import io.circe.{Decoder, Encoder}
import io.circe.generic.auto._
import sttp.tapir.Codec.PlainCodec
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.tests.data._
import sttp.tapir._

object Validation {
  type MyTaggedString = String @@ Tapir

  val in_query_tagged: PublicEndpoint[String @@ Tapir, Unit, Unit, Any] = {
    implicit def plainCodecForMyTaggedString: PlainCodec[MyTaggedString] =
      Codec.string.map(_.taggedWith[Tapir])(identity).validate(Validator.pattern("apple|banana"))

    endpoint.in(query[String @@ Tapir]("fruit"))
  }

  val in_query: PublicEndpoint[Int, Unit, Unit, Any] = {
    endpoint.in(query[Int]("amount").validate(Validator.min(0)))
  }

  val in_valid_json: PublicEndpoint[ValidFruitAmount, Unit, Unit, Any] = {
    implicit val schemaForIntWrapper: Schema[IntWrapper] = Schema(SchemaType.SInteger()).validate(Validator.min(1).contramap(_.v))
    implicit val schemaForStringWrapper: Schema[StringWrapper] =
      Schema.string.validate(Validator.minLength(4).contramap(_.v))
    implicit val intEncoder: Encoder[IntWrapper] = Encoder.encodeInt.contramap(_.v)
    implicit val intDecoder: Decoder[IntWrapper] = Decoder.decodeInt.map(IntWrapper.apply)
    implicit val stringEncoder: Encoder[StringWrapper] = Encoder.encodeString.contramap(_.v)
    implicit val stringDecoder: Decoder[StringWrapper] = Decoder.decodeString.map(StringWrapper.apply)
    endpoint.in(jsonBody[ValidFruitAmount])
  }

  val in_valid_optional_json: PublicEndpoint[Option[ValidFruitAmount], Unit, Unit, Any] = {
    implicit val schemaForIntWrapper: Schema[IntWrapper] = Schema(SchemaType.SInteger()).validate(Validator.min(1).contramap(_.v))
    implicit val schemaForStringWrapper: Schema[StringWrapper] =
      Schema.string.validate(Validator.minLength(4).contramap(_.v))
    implicit val intEncoder: Encoder[IntWrapper] = Encoder.encodeInt.contramap(_.v)
    implicit val intDecoder: Decoder[IntWrapper] = Decoder.decodeInt.map(IntWrapper.apply)
    implicit val stringEncoder: Encoder[StringWrapper] = Encoder.encodeString.contramap(_.v)
    implicit val stringDecoder: Decoder[StringWrapper] = Decoder.decodeString.map(StringWrapper.apply)
    endpoint.in(jsonBody[Option[ValidFruitAmount]])
  }

  val in_valid_query: PublicEndpoint[IntWrapper, Unit, Unit, Any] = {
    implicit def plainCodecForWrapper: PlainCodec[IntWrapper] =
      Codec.int.map(IntWrapper.apply(_))(_.v).validate(Validator.min(1).contramap(_.v))
    endpoint.in(query[IntWrapper]("amount"))
  }

  val in_valid_json_collection: PublicEndpoint[BasketOfFruits, Unit, Unit, Any] = {
    implicit val schemaForIntWrapper: Schema[IntWrapper] = Schema(SchemaType.SInteger()).validate(Validator.min(1).contramap(_.v))
    implicit val encoder: Encoder[IntWrapper] = Encoder.encodeInt.contramap(_.v)
    implicit val decode: Decoder[IntWrapper] = Decoder.decodeInt.map(IntWrapper.apply)

    implicit val schemaForStringWrapper: Schema[StringWrapper] =
      Schema.string.validate(Validator.minLength(4).contramap(_.v))
    implicit val stringEncoder: Encoder[StringWrapper] = Encoder.encodeString.contramap(_.v)
    implicit val stringDecoder: Decoder[StringWrapper] = Decoder.decodeString.map(StringWrapper.apply)

    import sttp.tapir.tests.data.BasketOfFruits._
    implicit def validatedListEncoder[T: Encoder]: Encoder[ValidatedList[T]] = implicitly[Encoder[List[T]]].contramap(identity)
    implicit def validatedListDecoder[T: Decoder]: Decoder[ValidatedList[T]] =
      implicitly[Decoder[List[T]]].map(_.taggedWith[BasketOfFruits])
    implicit def schemaForValidatedList[T: Schema]: Schema[ValidatedList[T]] =
      implicitly[Schema[T]].asIterable[List].validate(Validator.minSize(1)).map(l => Some(l.taggedWith[BasketOfFruits]))(l => l)
    endpoint.in(jsonBody[BasketOfFruits])
  }

  val in_valid_map: PublicEndpoint[Map[String, ValidFruitAmount], Unit, Unit, Any] = {
    // TODO: needed until Scala3 derivation supports
    implicit val schemaForStringWrapper: Schema[StringWrapper] = Schema(SchemaType.SString())
    implicit val encoderForStringWrapper: Encoder[StringWrapper] = Encoder.encodeString.contramap(_.v)
    implicit val decoderForStringWrapper: Decoder[StringWrapper] = Decoder.decodeString.map(StringWrapper.apply)

    implicit val schemaForIntWrapper: Schema[IntWrapper] = Schema(SchemaType.SInteger()).validate(Validator.min(1).contramap(_.v))
    implicit val encoderForIntWrapper: Encoder[IntWrapper] = Encoder.encodeInt.contramap(_.v)
    implicit val decoderForIntWrapper: Decoder[IntWrapper] = Decoder.decodeInt.map(IntWrapper.apply)
    endpoint.in(jsonBody[Map[String, ValidFruitAmount]])
  }

  val in_enum_class: PublicEndpoint[Color, Unit, Unit, Any] = {
    implicit def plainCodecForColor: PlainCodec[Color] = Codec.derivedEnumeration[String, Color](
      (_: String) match {
        case "red"  => Some(Red)
        case "blue" => Some(Blue)
        case _      => None
      },
      _.toString.toLowerCase
    )
    endpoint.in(query[Color]("color"))
  }

  val in_optional_enum_class: PublicEndpoint[Option[Color], Unit, Unit, Any] = {
    implicit def plainCodecForColor: PlainCodec[Color] = Codec.derivedEnumeration[String, Color](
      (_: String) match {
        case "red"  => Some(Red)
        case "blue" => Some(Blue)
        case _      => None
      },
      _.toString.toLowerCase
    )
    endpoint.in(query[Option[Color]]("color"))
  }

  val out_enum_object: PublicEndpoint[Unit, Unit, ColorValue, Any] = {
    implicit def schemaForColor: Schema[Color] =
      Schema.string.validate(
        Validator.enumeration(
          List(Blue, Red),
          {
            case Red  => Some("red")
            case Blue => Some("blue")
          }
        )
      )
    endpoint.out(jsonBody[ColorValue])
  }

  val in_enum_values: PublicEndpoint[IntWrapper, Unit, Unit, Any] = {
    implicit def plainCodecForWrapper: PlainCodec[IntWrapper] =
      Codec.int.map(IntWrapper.apply(_))(_.v).validate(Validator.enumeration(List(IntWrapper(1), IntWrapper(2))))
    endpoint.in(query[IntWrapper]("amount"))
  }

  val in_json_wrapper_enum: PublicEndpoint[ColorWrapper, Unit, Unit, Any] = {
    implicit def schemaForColor: Schema[Color] = Schema.derivedEnumeration[Color](encode = Some(_.toString.toLowerCase))
    endpoint.in(jsonBody[ColorWrapper])
  }

  val in_valid_int_array: PublicEndpoint[List[IntWrapper], Unit, Unit, Any] = {
    implicit val schemaForIntWrapper: Schema[IntWrapper] =
      Schema(SchemaType.SInteger()).validate(Validator.all(Validator.min(1), Validator.max(10)).contramap(_.v))
    implicit val encoder: Encoder[IntWrapper] = Encoder.encodeInt.contramap(_.v)
    implicit val decode: Decoder[IntWrapper] = Decoder.decodeInt.map(IntWrapper.apply)
    endpoint.in(jsonBody[List[IntWrapper]])
  }
}
