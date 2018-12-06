package tapir.json.circe

import tapir.DecodeResult.{Error, Value}
import tapir.{DecodeResult, MediaType, Schema, SchemaFor}
import tapir.TypeMapper.RequiredJsonTypeMapper
import io.circe.syntax._
import io.circe.{Decoder, Encoder}

trait JsonCirce {
  implicit val stringJsonTypeMapper: RequiredJsonTypeMapper[String] = new RequiredJsonTypeMapper[String] {
    override def toString(t: String): String = t.asJson.noSpaces
    override def fromString(s: String): DecodeResult[String] = Value(s)
    override def schema: Schema = Schema.SString
    override def mediaType = MediaType.Json()
  }
  implicit val intJsonTypeMapper: RequiredJsonTypeMapper[Int] = new RequiredJsonTypeMapper[Int] {
    override def toString(t: Int): String = t.asJson.noSpaces
    override def fromString(s: String): DecodeResult[Int] =
      try Value(s.toInt)
      catch {
        case e: Exception => Error(s, e, "Cannot parse integer")
      }
    override def schema: Schema = Schema.SInt
    override def mediaType = MediaType.Json()
  }
  implicit def objectTypeMapper[T: Encoder: Decoder: SchemaFor]: RequiredJsonTypeMapper[T] = new RequiredJsonTypeMapper[T] {
    override def toString(t: T): String = t.asJson.noSpaces
    override def fromString(s: String): DecodeResult[T] = io.circe.parser.decode[T](s) match {
      case Left(error) => Error(s, error, error.getMessage)
      case Right(v)    => Value(v)
    }
    override def schema: Schema = implicitly[SchemaFor[T]].schema
    override def mediaType = MediaType.Json()
  }

}
