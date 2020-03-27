package sttp.tapir.json.circe

import java.nio.charset.StandardCharsets

import io.circe._
import io.circe.syntax._
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.DecodeResult.{Error, Value}
import sttp.tapir.SchemaType._
import sttp.tapir._

trait TapirJsonCirce {
  implicit def encoderDecoderCodec[T: Encoder: Decoder: Schema: Validator]: JsonCodec[T] = new JsonCodec[T] {
    override def encode(t: T): String = jsonPrinter.print(t.asJson)
    override def rawDecode(s: String): DecodeResult[T] = io.circe.parser.decode[T](s) match {
      case Left(error) => Error(s, error)
      case Right(v)    => Value(v)
    }
    override def meta: CodecMeta[T, CodecFormat.Json, String] =
      CodecMeta(implicitly[Schema[T]], CodecFormat.Json(), StringValueType(StandardCharsets.UTF_8), implicitly[Validator[T]])
  }

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
}
