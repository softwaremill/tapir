package sttp.tapir.json.json4s.jackson

import org.json4s.jackson.Serialization
import org.json4s.{Formats, MappingException, NoTypeHints}
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.DecodeResult.Error.{JsonDecodeException, JsonError}
import sttp.tapir.DecodeResult.{Error, Value}
import sttp.tapir.{Codec, EndpointIO, Schema, anyFromUtf8StringBody}

trait TapirJson4sJackson {
  implicit val formats: Formats = Serialization.formats(NoTypeHints)

  def jsonBody[T: Manifest: Schema]: EndpointIO.Body[String, T] = anyFromUtf8StringBody(json4sJacksonCodec[T])

  implicit def json4sJacksonCodec[T: Manifest: Schema](implicit formats: Formats): JsonCodec[T] =
    Codec.json[T] { s =>
      try {
        Value(Serialization.read[T](s))
      } catch {
        case e: MappingException =>
          Error(s, JsonDecodeException(List(JsonError(e.msg, path = List.empty)), e))
      }
    } { t =>
      Serialization.write(t.asInstanceOf[AnyRef])
    }
}
