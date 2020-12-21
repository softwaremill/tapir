package sttp.tapir.json.play

import play.api.libs.json._
import sttp.tapir._
import sttp.tapir.SchemaType._
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.DecodeResult.Error.{JsonDecodeException, JsonError}
import sttp.tapir.DecodeResult.{Error, Value}

trait TapirJsonPlay {
  def jsonBody[T: Reads: Writes: Schema]: EndpointIO.Body[String, T] = anyFromUtf8StringBody(readsWritesCodec[T])

  implicit def readsWritesCodec[T: Reads: Writes: Schema]: JsonCodec[T] =
    Codec.json[T] { s =>
      implicitly[Reads[T]].reads(Json.parse(s)) match {
        case JsError(errors) =>
          val jsonErrors = errors
            .flatMap { case (path, validationErrors) =>
              val fields = path.toJsonString.split("\\.").toList.map(FieldName.apply)
              validationErrors.map(error => fields -> error)
            }
            .map { case (fields, validationError) =>
              JsonError(validationError.message, fields)
            }
            .toList
          Error(s, JsonDecodeException(jsonErrors, JsResultException(errors)))
        case JsSuccess(value, _) => Value(value)
      }
    } { t => Json.stringify(Json.toJson(t)) }

  implicit val schemaForPlayJsValue: Schema[JsValue] = Schema(
    SProduct(
      SObjectInfo("play.api.libs.json.JsValue"),
      List.empty
    )
  )
}
