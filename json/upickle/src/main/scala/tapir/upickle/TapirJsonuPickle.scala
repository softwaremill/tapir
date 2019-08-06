package tapir.json.upickle

import java.nio.charset.StandardCharsets
import scala.util.{Try, Success, Failure}
import tapir.Codec.JsonCodec
import tapir.DecodeResult.{Error, Value}
import tapir._
import upickle.default.{ReadWriter => RW}
import upickle.default.{read, write}

trait TapirJsonuPickle {

  implicit def encoderDecoderCodec[T: RW: SchemaFor, M]: JsonCodec[T] = new JsonCodec[T] {

    def encode(t: T): String = write(t)

    def decode(s: String): DecodeResult[T] = {
      Try(read[T](s)) match {
        case Success(v) => Value(v)
        case Failure(e) => Error("upickle decoder failed with:", e)
      }
    }

    def meta: CodecMeta[MediaType.Json, String] = {
      CodecMeta(implicitly[SchemaFor[T]].schema, MediaType.Json(), StringValueType(StandardCharsets.UTF_8))
    }
  }
}
