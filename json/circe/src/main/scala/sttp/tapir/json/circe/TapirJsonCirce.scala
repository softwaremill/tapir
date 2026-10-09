package sttp.tapir.json.circe

import cats.data.Validated
import io.circe._
import io.circe.syntax._
import sttp.tapir._
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.DecodeResult.{Error, Value}
import sttp.tapir.DecodeResult.Error.{JsonDecodeException, JsonError}
import sttp.tapir.Schema.SName

trait TapirJsonCirce {
  def jsonBody[T: Encoder: Decoder: Schema]: EndpointIO.Body[String, T] = stringBodyUtf8AnyFormat(circeCodec[T])

  def jsonBodyWithRaw[T: Encoder: Decoder: Schema]: EndpointIO.Body[String, (String, T)] = stringBodyUtf8AnyFormat(
    implicitly[JsonCodec[(String, T)]]
  )

  def jsonQuery[T: Encoder: Decoder: Schema](name: String): EndpointInput.Query[T] =
    queryAnyFormat[T, CodecFormat.Json](name, sttp.tapir.Codec.jsonQuery(circeCodec))

  implicit def circeCodec[T: Encoder: Decoder: Schema]: JsonCodec[T] =
    sttp.tapir.Codec.json[T] { s =>
      io.circe.parser.decodeAccumulating[T](s) match {
        case Validated.Valid(v)               => Value(v)
        case Validated.Invalid(circeFailures) =>
          val tapirJsonErrors = circeFailures.map {
            case ParsingFailure(msg, _)   => JsonError(msg, path = List.empty)
            case failure: DecodingFailure =>
              val path = CursorOp.opsToPath(failure.history)
              val fields = path.split("\\.").toList.filter(_.nonEmpty).map(FieldName.apply)
              JsonError(failure.message, fields)
          }

          Error(
            original = s,
            error = JsonDecodeException(
              errors = tapirJsonErrors.toList,
              underlying = Errors(circeFailures)
            )
          )
      }
    } { t => jsonPrinter.print(t.asJson) }

  def jsonPrinter: Printer = Printer.noSpaces

  implicit val schemaForCirceJson: Schema[Json] = Schema.any
  implicit val schemaForCirceJsonObject: Schema[JsonObject] = Schema.anyObject[JsonObject].name(SName("io.circe.JsonObject"))
}
