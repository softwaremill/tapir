package sttp.tapir.json.spray

import spray.json._
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.DecodeResult.Error.{JsonDecodeException, JsonError}
import sttp.tapir.DecodeResult.{Error, Value}
import sttp.tapir.SchemaType._
import sttp.tapir._

import scala.util.{Failure, Success, Try}

trait TapirJsonSpray {
  def jsonBody[T: JsonFormat: Schema]: EndpointIO.Body[String, T] = anyFromUtf8StringBody(jsonFormatCodec[T])

  implicit def jsonFormatCodec[T: JsonFormat: Schema]: JsonCodec[T] =
    Codec.json { s =>
      Try(s.parseJson.convertTo[T]) match {
        case Success(v) => Value(v)
        case Failure(e @ DeserializationException(msg, _, fieldNames)) =>
          val errors = fieldNames.map(field => JsonError(msg, Some(field)))
          Error(s, JsonDecodeException(errors, e))
        case Failure(e) =>
          Error(s, JsonDecodeException(errors = List.empty, e))
      }
    } { t => t.toJson.toString() }

  implicit val schemaForSprayJsValue: Schema[JsValue] = Schema(
    SProduct(
      SObjectInfo("spray.json.JsValue"),
      List.empty
    )
  )
}
