package sttp.tapir.json.zio

import sttp.tapir.Codec.JsonCodec
import sttp.tapir.DecodeResult.Error.{JsonDecodeException, JsonError}
import sttp.tapir.DecodeResult.{Error, Value}
import sttp.tapir.SchemaType.{SCoproduct, SObjectInfo, SProduct}
import sttp.tapir.{EndpointIO, FieldName, Schema, anyFromUtf8StringBody}
import zio.json.ast.Json
import zio.json.ast.Json.Obj
import zio.json.{JsonDecoder, JsonEncoder, _}

import scala.collection.immutable.ListMap

trait TapirJsonZio {

  def jsonBody[T: JsonEncoder: JsonDecoder: Schema]: EndpointIO.Body[String, T] = anyFromUtf8StringBody(zioCodec[T])

  implicit def zioCodec[T: JsonEncoder: JsonDecoder: Schema]: JsonCodec[T] =
    sttp.tapir.Codec.json[T] { s =>
      zio.json.JsonDecoder.apply[T].decodeJson(s) match {
        case Left(error) =>
          val (message, path) = parseErrorMessage(error)
          Error(s, JsonDecodeException(List(JsonError(message, path)), new Exception(error)))
        case Right(value) => Value(value)
      }
    } { t => t.toJson }

  private def parseErrorMessage(errorMessage: String): (String, List[FieldName]) = {
    val leftParenIndex = errorMessage.indexOf('(')
    if (leftParenIndex >= 0) {
      val path = errorMessage.substring(0, leftParenIndex)
      val message = errorMessage.substring(leftParenIndex + 1, errorMessage.length - 1)
      return message -> path.split("\\.").toList.filter(_.nonEmpty).map(FieldName.apply)
    } else {
      return errorMessage -> List.empty
    }
  }

  // JsValue is a coproduct with unknown implementations
  implicit val schemaForZioJsonValue: Schema[Json] =
    Schema(
      SCoproduct(
        SObjectInfo("zio.json.ast.Json"),
        ListMap.empty,
        None
      )(_ => None)
    )

  implicit val schemaForZioJsonObject: Schema[Obj] =
    Schema(
      SProduct(
        SObjectInfo("zio.json.ast.Json.Obj"),
        Nil
      )
    )

}
