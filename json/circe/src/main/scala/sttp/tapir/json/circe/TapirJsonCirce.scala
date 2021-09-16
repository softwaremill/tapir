package sttp.tapir.json.circe

import cats.data.Validated
import io.circe._
import io.circe.syntax._
import sttp.tapir._
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.DecodeResult.{Error, Value}
import sttp.tapir.DecodeResult.Error.{JsonDecodeException, JsonError}
import sttp.tapir.Schema.SName
import sttp.tapir.SchemaType._

trait TapirJsonCirce {
  def jsonBody[T: Encoder: Decoder: Schema]: EndpointIO.Body[String, T] = anyFromUtf8StringBody(circeCodec[T])

  implicit def circeCodec[T: Encoder: Decoder: Schema]: JsonCodec[T] =
    sttp.tapir.Codec.json[T] { s =>
      io.circe.parser.decodeAccumulating[T](s) match {
        case Validated.Valid(v) => Value(v)
        case Validated.Invalid(circeFailures) =>
          val tapirJsonErrors = circeFailures.map {
            case ParsingFailure(msg, _) => JsonError(msg, path = List.empty)
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

  // Json is a coproduct with unknown implementations
  implicit val schemaForCirceJson: Schema[Json] =
    Schema(
      SCoproduct(Nil, None)(_ => None),
      None
    )

  implicit val schemaForCirceJsonObject: Schema[JsonObject] = Schema(SProduct(Nil), Some(SName("io.circe.JsonObject")))

  // #321: circe encodes big decimals as numbers - adjusting the schemas (which by default are strings) to that format
  implicit val schemaForBigDecimal: Schema[BigDecimal] = Schema(SNumber())
  implicit val schemaForJBigDecimal: Schema[java.math.BigDecimal] = Schema(SNumber())
}
