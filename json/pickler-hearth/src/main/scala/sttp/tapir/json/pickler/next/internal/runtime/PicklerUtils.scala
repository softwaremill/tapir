package sttp.tapir.json.pickler.next.internal.runtime

import com.github.plokhotnyuk.jsoniter_scala.core.{
  readFromString,
  writeToString,
  JsonReader,
  JsonReaderException,
  JsonValueCodec,
  ReaderConfig
}
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.DecodeResult.Error.{JsonDecodeException, JsonError}
import sttp.tapir.DecodeResult.{Error, Value}
import sttp.tapir.{Codec, Schema, SchemaType}

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

  /** `null` for reference types, the boxed zero for primitives. Used as jsoniter's `nullValue`. */
  def nullValueOf[A]: A = null.asInstanceOf[A]

  // -- Phase 0 placeholders -------------------------------------------------------------------------------------
  // These exist only so that the macro skeleton produces type-correct, inspectable trees before any derivation
  // rules are implemented. They are replaced rule-by-rule in Phase 1+ and must not survive into a release.

  /** Placeholder schema, so the skeleton can emit a `Schema[A]` before the schema rules exist. */
  def notImplementedSchema[A](typeName: String): Schema[A] =
    Schema[A](SchemaType.SString()).description(s"TODO: schema derivation not implemented for $typeName")

  /** Placeholder decoder body. Returns `A` (via `Nothing`) so the skeleton type-checks without the decoder rules. */
  def notImplementedDecode[A](in: JsonReader, typeName: String): A =
    in.decodeError(s"TODO: decoder derivation not implemented for $typeName")
}
