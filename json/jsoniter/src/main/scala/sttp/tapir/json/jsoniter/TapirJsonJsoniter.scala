package sttp.tapir.json.jsoniter

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.DecodeResult.{Error, Value}
import sttp.tapir.{EndpointIO, Schema, Validator, anyFromUtf8StringBody}
import com.github.plokhotnyuk.jsoniter_scala.core._
import sttp.tapir.DecodeResult.Error.JsonDecodeException

import scala.util.{Failure, Success, Try}

trait TapirJsonJsoniter {
  def jsonBody[T: JsonValueCodec: Schema]: EndpointIO.Body[String, T] = anyFromUtf8StringBody(jsoniterCodec[T])

  implicit def jsoniterCodec[T: JsonValueCodec: Schema]: JsonCodec[T] =
    sttp.tapir.Codec.json { s =>
      Try(readFromString[T](s)) match {
        case Failure(error) => Error(s, JsonDecodeException(errors = List.empty, error))
        case Success(v)     => Value(v)
      }
    } { t => writeToString[T](t) }
}
