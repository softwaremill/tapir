package tapir.json.circe

import java.nio.charset.StandardCharsets

import tapir.DecodeResult.{Error, Value}
import tapir.{CodecMeta, DecodeResult, MediaType, StringValueType}
import io.circe.syntax._
import io.circe.{Decoder, Encoder}
import tapir.Codec.JsonCodec
import tapir.SchemaFor

trait TapirJsonCirce {
  implicit def encoderDecoderCodec[T: Encoder: Decoder: SchemaFor]: JsonCodec[T] = new JsonCodec[T] {
    override def encode(t: T): String = t.asJson.noSpaces
    override def decode(s: String): DecodeResult[T] = io.circe.parser.decode[T](s) match {
      case Left(error) => Error(s, error)
      case Right(v)    => Value(v)
    }
    override def meta: CodecMeta[MediaType.Json, String] =
      CodecMeta(implicitly[SchemaFor[T]].schema, MediaType.Json(), StringValueType(StandardCharsets.UTF_8))
  }
}
