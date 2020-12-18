package sttp.tapir.json.circe

import io.circe._
import io.circe.syntax._
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.DecodeResult.Error.{JsonDecodeException, JsonError}
import sttp.tapir.DecodeResult.{Error, Value}
import sttp.tapir.SchemaType._
import sttp.tapir._

trait TapirJsonCirce {
  def jsonBody[T: Encoder: Decoder: Schema]: EndpointIO.Body[String, T] = anyFromUtf8StringBody(circeCodec[T])

  implicit def circeCodec[T: Encoder: Decoder: Schema]: JsonCodec[T] =
    sttp.tapir.Codec.json[T] { s =>
      io.circe.parser.decode[T](s) match {
        case Left(failure @ ParsingFailure(msg, _)) =>
          Error(s, JsonDecodeException(List(JsonError(msg, atPath = None)), failure))
        case Left(failure: DecodingFailure) =>
          val path = CursorOp.opsToPath(failure.history)
          Error(s, JsonDecodeException(List(JsonError(failure.message, Some(path))), failure))
        case Right(v) => Value(v)
      }
    } { t => jsonPrinter.print(t.asJson) }

  def jsonPrinter: Printer = Printer.noSpaces

  // Json is a coproduct with unknown implementations
  implicit val schemaForCirceJson: Schema[Json] =
    Schema(
      SCoproduct(
        SObjectInfo("io.circe.Json"),
        List.empty,
        None
      )
    )

  implicit val schemaForCirceJsonObject: Schema[JsonObject] =
    Schema(
      SProduct(
        SObjectInfo("io.circe.JsonObject"),
        Iterable.empty
      )
    )
}
