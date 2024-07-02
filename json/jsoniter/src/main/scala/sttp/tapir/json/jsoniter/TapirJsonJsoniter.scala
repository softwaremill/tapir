package sttp.tapir.json.jsoniter

import com.github.plokhotnyuk.jsoniter_scala.core._
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.DecodeResult.Error.{JsonDecodeException, JsonError}
import sttp.tapir.DecodeResult.{Error, Value}
import sttp.tapir._

import scala.util.{Failure, Success, Try}

trait TapirJsonJsoniter {
  lazy val readerConfig = ReaderConfig.withAppendHexDumpToParseException(false)
  def jsonBody[T: JsonValueCodec: Schema]: EndpointIO.Body[String, T] = stringBodyUtf8AnyFormat(jsoniterCodec[T])

  def jsonBodyWithRaw[T: JsonValueCodec: Schema]: EndpointIO.Body[String, (String, T)] = stringBodyUtf8AnyFormat(
    implicitly[JsonCodec[(String, T)]]
  )

  def jsonQuery[T: JsonValueCodec: Schema](name: String): EndpointInput.Query[T] =
    queryAnyFormat[T, CodecFormat.Json](name, Codec.jsonQuery(jsoniterCodec))

  implicit def jsoniterCodec[T: JsonValueCodec: Schema]: JsonCodec[T] =
    sttp.tapir.Codec.json { s =>
      Try(readFromString[T](s, readerConfig)) match {
        case Failure(error: JsonReaderException) =>
          val errMsg = Option(error.getMessage)
          Error(s, JsonDecodeException(errors = errMsg.toList.map(e => JsonError(e, Nil)), error))
        case Failure(error) => Error(s, JsonDecodeException(errors = List.empty, error))
        case Success(v)     => Value(v)
      }
    } { t => writeToString[T](t) }
}
