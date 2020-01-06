package sttp.tapir.json.upickle

import java.nio.charset.StandardCharsets
import scala.util.{Try, Success, Failure}
import sttp.tapir._
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.DecodeResult.{Error, Value}
import upickle.default.{ReadWriter, read, write}

trait TapirJsonuPickle {
  implicit def encoderDecoderCodec[T: ReadWriter: Schema]: JsonCodec[T] = new JsonCodec[T] {
    def encode(t: T): String = write(t)

    def rawDecode(s: String): DecodeResult[T] = {
      Try(read[T](s)) match {
        case Success(v) => Value(v)
        case Failure(e) => Error("upickle decoder failed", e)
      }
    }

    def meta: CodecMeta[T, CodecFormat.Json, String] = {
      CodecMeta(implicitly[Schema[T]], CodecFormat.Json(), StringValueType(StandardCharsets.UTF_8))
    }
  }
}
