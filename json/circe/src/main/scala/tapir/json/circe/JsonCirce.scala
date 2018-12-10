package tapir.json.circe

import tapir.DecodeResult.{Error, Value}
import tapir.{DecodeResult, MediaType, Schema, SchemaFor}
import tapir.TypeMapper.RequiredJsonTypeMapper
import io.circe.syntax._
import io.circe.{Decoder, Encoder}

trait JsonCirce {
  implicit def encoderDecoderTypeMapper[T: Encoder: Decoder: SchemaFor]: RequiredJsonTypeMapper[T] = new RequiredJsonTypeMapper[T] {
    override def toString(t: T): String = t.asJson.noSpaces
    override def fromString(s: String): DecodeResult[T] = io.circe.parser.decode[T](s) match {
      case Left(error) => Error(s, error, error.getMessage)
      case Right(v)    => Value(v)
    }
    override def schema: Schema = implicitly[SchemaFor[T]].schema
    override def mediaType = MediaType.Json()
  }
}
