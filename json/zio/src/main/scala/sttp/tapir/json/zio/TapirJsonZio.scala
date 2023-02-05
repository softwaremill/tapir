package sttp.tapir.json.zio

import sttp.tapir.Codec.JsonCodec
import sttp.tapir.DecodeResult.Error.{JsonDecodeException, JsonError}
import sttp.tapir.DecodeResult.{Error, Value}
import sttp.tapir.Schema.SName
import sttp.tapir.SchemaType.{SCoproduct, SProduct}
import sttp.tapir._
import zio.json.ast.Json
import zio.json.ast.Json.Obj
import zio.json.{JsonDecoder, JsonEncoder, _}

trait TapirJsonZio {

  def jsonBody[T: JsonEncoder: JsonDecoder: Schema]: EndpointIO.Body[String, T] = stringBodyUtf8AnyFormat(zioCodec[T])

  def jsonBodyWithRaw[T: JsonEncoder: JsonDecoder: Schema]: EndpointIO.Body[String, (String, T)] = stringBodyUtf8AnyFormat(
    implicitly[JsonCodec[(String, T)]]
  )

  def jsonQuery[T: JsonEncoder: JsonDecoder: Schema](name: String): EndpointInput.Query[T] =
    queryAnyFormat[T, CodecFormat.Json](name, implicitly)

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

  implicit val schemaForZioJsonValue: Schema[Json] = Schema.any
  implicit val schemaForZioJsonObject: Schema[Obj] = Schema.anyObject[Obj].name(SName("zio.json.ast.Json.Obj"))
}
