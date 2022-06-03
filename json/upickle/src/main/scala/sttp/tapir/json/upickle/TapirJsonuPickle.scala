package sttp.tapir.json.upickle

import scala.util.{Failure, Success, Try}
import sttp.tapir._
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.DecodeResult.Error.JsonDecodeException
import sttp.tapir.DecodeResult.{Error, Value}
import upickle.default.{ReadWriter, read, write}

trait TapirJsonuPickle {

  def jsonBody[T: ReadWriter: Schema]: EndpointIO.Body[String, T] = stringBodyUtf8AnyFormat(readWriterCodec[T])

  def jsonBodyWithRaw[T: ReadWriter: Schema]: EndpointIO.Body[String, (String, T)] = stringBodyUtf8AnyFormat(
    implicitly[JsonCodec[(String, T)]]
  )

  implicit def readWriterCodec[T: ReadWriter: Schema]: JsonCodec[T] =
    Codec.json[T] { s =>
      Try(read[T](s)) match {
        case Success(v) => Value(v)
        case Failure(e) => Error(s, JsonDecodeException(errors = List.empty, e))
      }
    } { t => write(t) }
}
