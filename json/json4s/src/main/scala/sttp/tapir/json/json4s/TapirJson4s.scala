package sttp.tapir.json.json4s

import org.json4s._
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.DecodeResult.Error.{JsonDecodeException, JsonError}
import sttp.tapir.DecodeResult.{Error, Value}
import sttp.tapir.SchemaType.SCoproduct
import sttp.tapir.{Codec, EndpointIO, Schema, stringBodyUtf8AnyFormat}

trait TapirJson4s {
  def jsonBody[T: Manifest: Schema](implicit formats: Formats, serialization: Serialization): EndpointIO.Body[String, T] =
    stringBodyUtf8AnyFormat(json4sCodec[T])

  def jsonBodyWithRaw[T: Manifest: Schema](implicit formats: Formats, serialization: Serialization): EndpointIO.Body[String, (String, T)] =
    stringBodyUtf8AnyFormat(
      implicitly[JsonCodec[(String, T)]]
    )

  implicit def json4sCodec[T: Manifest: Schema](implicit formats: Formats, serialization: Serialization): JsonCodec[T] =
    Codec.json[T] { s =>
      try {
        Value(serialization.read[T](s))
      } catch {
        case e: MappingException =>
          Error(s, JsonDecodeException(List(JsonError(e.msg, path = List.empty)), e))
      }
    } { t =>
      serialization.write(t.asInstanceOf[AnyRef])
    }

  // JValue is a coproduct with unknown implementations
  implicit val schemaForJson4s: Schema[JValue] =
    Schema(
      SCoproduct(Nil, None)(_ => None),
      None
    )
}
