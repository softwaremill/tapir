package sttp.tapir.json.play

import play.api.libs.json._
import sttp.tapir._
import sttp.tapir.SchemaType._
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.DecodeResult.Error.{JsonDecodeException, JsonError}
import sttp.tapir.DecodeResult.{Error, Value}
import sttp.tapir.Schema.SName

import scala.util.{Failure, Success, Try}

trait TapirJsonPlay {
  def jsonBody[T: Reads: Writes: Schema]: EndpointIO.Body[String, T] = anyFromUtf8StringBody(readsWritesCodec[T])

  implicit def readsWritesCodec[T: Reads: Writes: Schema]: JsonCodec[T] =
    Codec.json[T] { s =>
      Try(Json.parse(s)) match {
        case Failure(exception) =>
          Error(s, JsonDecodeException(List.empty, exception))
        case Success(jsValue) =>
          implicitly[Reads[T]].reads(jsValue) match {
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
            case JsSuccess(value, _) =>
              Value(value)
          }
      }
    } { t => Json.stringify(Json.toJson(t)) }

  // JsValue is a coproduct with unknown implementations
  implicit val schemaForPlayJsValue: Schema[JsValue] =
    Schema(
      SCoproduct(Nil, None)(_ => None),
      None
    )

  implicit val schemaForPlayJsObject: Schema[JsObject] =
    Schema(SProduct(Nil), Some(SName("play.api.libs.json.JsObject")))
}
