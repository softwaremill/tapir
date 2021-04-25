package sttp.tapir.json.zio

import sttp.tapir.Codec.JsonCodec
import sttp.tapir.DecodeResult.Error.{JsonDecodeException, JsonError}
import sttp.tapir.DecodeResult.{Error, Value}
import sttp.tapir.SchemaType._
import sttp.tapir._
import zio.json.ast.Json
import zio.json.{DecoderOps, EncoderOps, JsonDecoder, JsonEncoder}

import scala.collection.immutable.ListMap

trait TapirZioJson {
  def jsonBody[T: JsonEncoder: JsonDecoder: Schema]: EndpointIO.Body[String, T] = anyFromUtf8StringBody(zioJsonCodec[T])

  implicit def zioJsonCodec[T: JsonEncoder: JsonDecoder: Schema]: JsonCodec[T] =
    sttp.tapir.Codec.json[T] { s =>
      s.fromJson[T] match {
        case Left(msg) =>
          val (message, path) = parseErrorMessage(msg)
          Error(s, JsonDecodeException(List(JsonError(message, path)), new Exception(msg)))
        case Right(v) => Value(v)
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

  // Json is a coproduct with unknown implementations
  implicit val schemaForZioJson: Schema[Json] =
    Schema(
      SCoproduct(
        SObjectInfo("zio.json.ast.Json"),
        ListMap.empty,
        None
      )(_ => None)
    )

  implicit val schemaForZioJsonObject: Schema[Json.Obj] =
    Schema(
      SProduct(
        SObjectInfo("zio.json.ast.Json.Obj"),
        Nil
      )
    )
}
