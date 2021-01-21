package sttp.tapir.json.json4s.native

import org.json4s._
import org.json4s.native.Serialization
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.DecodeResult.Error.{JsonDecodeException, JsonError}
import sttp.tapir.DecodeResult.{Error, Value}
import sttp.tapir.SchemaType._
import sttp.tapir._

trait TapirJson4sNative {
  implicit val formats: Formats = Serialization.formats(NoTypeHints)

  def jsonBody[T: Manifest :Schema]: EndpointIO.Body[String, T] = anyFromUtf8StringBody(json4sNativeCodec[T])

  implicit def json4sNativeCodec[T: Manifest :Schema](implicit formats: Formats): JsonCodec[T] =
    Codec.json[T] { s =>
      try {
        Value(Serialization.read[T](s))
      } catch {
        case e: MappingException =>
          Error(s, JsonDecodeException(List(JsonError(e.msg, path = List.empty)), e))
      }
    } {
      t => Serialization.write(t)
    }

  implicit val schemaForJson4s: Schema[JValue] =
    Schema(
      SCoproduct(
        SObjectInfo("org.json4s.JValue"),
        List.empty,
        None
      )
    )
}
