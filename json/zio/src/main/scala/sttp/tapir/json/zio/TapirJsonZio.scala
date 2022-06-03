package sttp.tapir.json.zio

import sttp.tapir.Codec.JsonCodec
import sttp.tapir.DecodeResult.Error.{JsonDecodeException, JsonError}
import sttp.tapir.DecodeResult.{Error, Value}
import sttp.tapir.Schema.SName
import sttp.tapir.SchemaType.{SCoproduct, SProduct}
import sttp.tapir.{EndpointIO, FieldName, Schema, stringBodyUtf8AnyFormat}
import zio.json.ast.Json
import zio.json.ast.Json.Obj
import zio.json.{JsonDecoder, JsonEncoder, _}

trait TapirJsonZio {

  def jsonBody[T: JsonEncoder: JsonDecoder: Schema]: EndpointIO.Body[String, T] = stringBodyUtf8AnyFormat(zioCodec[T])

  def jsonBodyWithRaw[T: JsonEncoder: JsonDecoder: Schema]: EndpointIO.Body[String, (String, T)] = stringBodyUtf8AnyFormat(
    implicitly[JsonCodec[(String, T)]]
  )

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
      message -> path.split("\\.").toList.filter(_.nonEmpty).map(FieldName.apply)
    } else {
      errorMessage -> List.empty
    }
  }

  // JsValue is a coproduct with unknown implementations
  implicit val schemaForZioJsonValue: Schema[Json] =
    Schema(
      SCoproduct(Nil, None)(_ => None),
      None
    )

  implicit val schemaForZioJsonObject: Schema[Obj] =
    Schema(SProduct(Nil), Some(SName("zio.json.ast.Json.Obj")))
}
