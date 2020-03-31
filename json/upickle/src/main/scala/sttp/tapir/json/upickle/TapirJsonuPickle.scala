package sttp.tapir.json.upickle

import scala.util.{Try, Success, Failure}
import sttp.tapir._
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.DecodeResult.{Error, Value}
import upickle.default.{ReadWriter, read, write}

trait TapirJsonuPickle {

  def jsonBody[T: ReadWriter: Schema: Validator]: EndpointIO.Body[String, T] = anyFromUtf8StringBody(readWriterCodec[T])

  implicit def readWriterCodec[T: ReadWriter: Schema: Validator]: JsonCodec[T] =
    Codec.json[T] { s =>
      Try(read[T](s)) match {
        case Success(v) => Value(v)
        case Failure(e) => Error("upickle decoder failed", e)
      }
    } { t => write(t) }
}
