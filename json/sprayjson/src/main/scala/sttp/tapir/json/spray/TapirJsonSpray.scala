package sttp.tapir.json.spray

import spray.json._
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.DecodeResult.{Error, Value}
import sttp.tapir.SchemaType._
import sttp.tapir._

import scala.util.{Failure, Success, Try}

trait TapirJsonSpray {
  def jsonBody[T: JsonFormat: Schema: Validator]: EndpointIO.Body[String, T] = anyFromUtf8StringBody(jsonFormatCodec[T])

  implicit def jsonFormatCodec[T: JsonFormat: Schema: Validator]: JsonCodec[T] =
    Codec.json { s =>
      Try(s.parseJson.convertTo[T]) match {
        case Success(v) => Value(v)
        case Failure(e) => Error("spray json decoder failed", e)
      }
    } { t => t.toJson.toString() }

  implicit val schemaForSprayJsValue: Schema[JsValue] = Schema(
    SProduct(
      SObjectInfo("spray.json.JsValue"),
      List.empty
    )
  )
}
