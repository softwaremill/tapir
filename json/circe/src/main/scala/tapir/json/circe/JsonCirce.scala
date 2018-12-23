package tapir.json.circe

import tapir.DecodeResult.{Error, Value}
import tapir.{DecodeResult, MediaType, RawValueType, Schema, SchemaFor, StringValueType}
import tapir.Codec.RequiredJsonCodec
import io.circe.syntax._
import io.circe.{Decoder, Encoder}

trait JsonCirce {
  implicit def encoderDecoderCodec[T: Encoder: Decoder: SchemaFor]: RequiredJsonCodec[T] = new RequiredJsonCodec[T] {
    override val rawValueType: RawValueType[String] = StringValueType

    override def encode(t: T): String = t.asJson.noSpaces
    override def decode(s: String): DecodeResult[T] = io.circe.parser.decode[T](s) match {
      case Left(error) => Error(s, error, error.getMessage)
      case Right(v)    => Value(v)
    }
    override def schema: Schema = implicitly[SchemaFor[T]].schema
    override def mediaType = MediaType.Json()
  }
}
