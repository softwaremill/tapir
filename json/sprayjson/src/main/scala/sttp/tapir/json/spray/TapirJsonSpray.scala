package sttp.tapir.json.spray

import spray.json._
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.DecodeResult.Error.{JsonDecodeException, JsonError}
import sttp.tapir.DecodeResult.{Error, Value}
import sttp.tapir.Schema.SName
import sttp.tapir.SchemaType._
import sttp.tapir._

import scala.util.{Failure, Success, Try}

trait TapirJsonSpray {
  def jsonBody[T: JsonFormat: Schema]: EndpointIO.Body[String, T] = stringBodyUtf8AnyFormat(jsonFormatCodec[T])

  def jsonBodyWithRaw[T: JsonFormat: Schema]: EndpointIO.Body[String, (String, T)] = stringBodyUtf8AnyFormat(
    implicitly[JsonCodec[(String, T)]]
  )

  def jsonQuery[T: JsonFormat: Schema](name: String): EndpointInput.Query[T] =
    queryAnyFormat[T, CodecFormat.Json](name, Codec.jsonQuery(jsonFormatCodec))

  implicit def jsonFormatCodec[T: JsonFormat: Schema]: JsonCodec[T] =
    Codec.json { s =>
      Try(s.parseJson.convertTo[T]) match {
        case Success(v)                                                => Value(v)
        case Failure(e @ DeserializationException(msg, _, fieldNames)) =>
          val path = fieldNames.map(FieldName.apply)
          Error(s, JsonDecodeException(List(JsonError(msg, path)), e))
        case Failure(e) =>
          Error(s, JsonDecodeException(errors = List.empty, e))
      }
    } { t => t.toJson.toString }

  implicit val schemaForSprayJsValue: Schema[JsValue] = Schema.any
  implicit val schemaForSprayJsObject: Schema[JsObject] = Schema.anyObject[JsObject].name(SName("spray.json.JsObject"))
}
