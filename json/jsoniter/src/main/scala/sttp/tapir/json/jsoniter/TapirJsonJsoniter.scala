package sttp.tapir.json.jsoniter

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.DecodeResult.{Error, Value}
import sttp.tapir.{EndpointIO, Schema, Validator, anyFromUtf8StringBody}
import com.github.plokhotnyuk.jsoniter_scala.core._

import scala.util.{Failure, Success, Try}

trait TapirJsonJsoniter {
  def jsonBody[T: JsonValueCodec: Schema: Validator]: EndpointIO.Body[String, T] = anyFromUtf8StringBody(circeCodec[T])

  implicit def circeCodec[T: JsonValueCodec: Schema: Validator]: JsonCodec[T] =
    sttp.tapir.Codec.json { s =>
      Try(readFromString[T](s)) match {
        case Failure(error) => Error(s, error)
        case Success(v)     => Value(v)
      }
    } { t => writeToString[T](t) }
}
