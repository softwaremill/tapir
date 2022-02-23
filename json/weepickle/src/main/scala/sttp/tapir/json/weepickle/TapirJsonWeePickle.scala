package sttp.tapir.json.weepickle

import scala.util.{Failure, Success, Try}
import sttp.tapir._
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.DecodeResult.Error.JsonDecodeException
import sttp.tapir.DecodeResult.{Error, Value}
import com.rallyhealth.weepickle.v1.WeePickle._
import com.rallyhealth.weejson.v1.jackson._

trait TapirJsonWeePickle {

  def jsonBody[T: FromTo: Schema]: EndpointIO.Body[String, T] = anyFromUtf8StringBody(fromToCodec[T])

  implicit def fromToCodec[T: FromTo: Schema]: JsonCodec[T] =
    Codec.json[T] { s =>
      Try(FromJson(s).transform(ToScala[T])) match {
        case Success(v) => Value(v)
        case Failure(e) => Error(s, JsonDecodeException(errors = List.empty, e))
      }
    } { t => FromScala(t).transform(ToJson.string) }
}
