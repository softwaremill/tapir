package sttp.tapir.json.upickle

import scala.util.{Try, Success, Failure}
import sttp.tapir._
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.DecodeResult.{InvalidJson, Value}
import upickle.default.{ReadWriter, read, write}

trait TapirJsonuPickle {

  def jsonBody[T: ReadWriter: Schema]: EndpointIO.Body[String, T] = anyFromUtf8StringBody(readWriterCodec[T])

  implicit def readWriterCodec[T: ReadWriter: Schema]: JsonCodec[T] =
    Codec.json[T] { s =>
      Try(read[T](s)) match {
        case Success(v) => Value(v)
        case Failure(e) => InvalidJson(s, List.empty, e)
      }
    } { t => write(t) }
}
