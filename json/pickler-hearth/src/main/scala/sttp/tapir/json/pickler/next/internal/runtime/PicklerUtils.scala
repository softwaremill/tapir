package sttp.tapir.json.pickler.next.internal.runtime

import com.github.plokhotnyuk.jsoniter_scala.core.{readFromString, writeToString, JsonReaderException, JsonValueCodec, ReaderConfig}
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.DecodeResult.Error.{JsonDecodeException, JsonError}
import sttp.tapir.DecodeResult.{Error, Value}
import sttp.tapir.{Codec, Schema}
import sttp.tapir.json.pickler.next.Pickler

import scala.util.{Failure, Success, Try}

/** Runtime helpers invoked by macro-generated code, plus the bridge from a jsoniter-scala codec to a tapir codec.
  *
  * Everything here must be public (the generated code lives in user compilation units) and must not depend on any
  * macro machinery.
  */
object PicklerUtils {

  private lazy val readerConfig = ReaderConfig.withAppendHexDumpToParseException(false)

  /** Mirrors `sttp.tapir.json.jsoniter.TapirJsonJsoniter.jsoniterCodec`, kept here so that this module does not have to
    * depend on `tapir-jsoniter-scala`.
    */
  def toTapirCodec[A](codec: JsonValueCodec[A], schema: Schema[A]): JsonCodec[A] = {
    given JsonValueCodec[A] = codec
    given Schema[A] = schema
    Codec.json[A] { s =>
      Try(readFromString[A](s, readerConfig)) match {
        case Success(v) => Value(v)
        case Failure(error: JsonReaderException) =>
          val errMsg = Option(error.getMessage)
          Error(s, JsonDecodeException(errors = errMsg.toList.map(e => JsonError(e, Nil)), error))
        case Failure(error) => Error(s, JsonDecodeException(errors = List.empty, error))
      }
    } { a => writeToString[A](a) }
  }

  /** The `(value, schema)` pairs `Schema.oneOfUsingField` wants, from the `(value, pickler)` pairs the user gave. */
  def oneOfSchemas[T, V](mapping: (V, Pickler[? <: T])*): Seq[(V, Schema[?])] =
    mapping.map { case (v, p) => (v, p.schema) }
}
