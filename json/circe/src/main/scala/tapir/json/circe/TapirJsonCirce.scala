package tapir.json.circe

import java.nio.charset.StandardCharsets

import io.circe._
import io.circe.syntax._
import tapir.Codec.JsonCodec
import tapir.DecodeResult.{Error, Value}
import tapir.Schema._
import tapir._

trait TapirJsonCirce {
  implicit def encoderDecoderCodec[T: Encoder: Decoder: SchemaFor]: JsonCodec[T] = new JsonCodec[T] {
    override def encode(t: T): String = jsonPrinter.pretty(t.asJson)
    override def decode(s: String): DecodeResult[T] = io.circe.parser.decode[T](s) match {
      case Left(error) => Error(s, error)
      case Right(v)    => Value(v)
    }
    override def meta: CodecMeta[MediaType.Json, String] =
      CodecMeta(implicitly[SchemaFor[T]].schema, MediaType.Json(), StringValueType(StandardCharsets.UTF_8))
  }

  def jsonPrinter: Printer = Printer.noSpaces

  implicit val schemaForCirceJson: SchemaFor[Json] =
    SchemaFor(
      SProduct(
        SObjectInfo("io.circe.Json"),
        List.empty,
        List.empty
      )
    )
}
