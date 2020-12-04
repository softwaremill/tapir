package sttp.tapir.json.spray

import spray.json._
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.DecodeResult.{InvalidJson, Value}
import sttp.tapir.SchemaType._
import sttp.tapir._

import scala.util.{Failure, Success, Try}

trait TapirJsonSpray {
  def jsonBody[T: JsonFormat: Schema]: EndpointIO.Body[String, T] = anyFromUtf8StringBody(jsonFormatCodec[T])

  implicit def jsonFormatCodec[T: JsonFormat: Schema]: JsonCodec[T] =
    Codec.json { s =>
      Try(s.parseJson.convertTo[T]) match {
        case Success(v) => Value(v)
        case Failure(e) => InvalidJson(s, e)
      }
    } { t => t.toJson.toString() }

  implicit val schemaForSprayJsValue: Schema[JsValue] = Schema(
    SProduct(
      SObjectInfo("spray.json.JsValue"),
      List.empty
    )
  )
}
