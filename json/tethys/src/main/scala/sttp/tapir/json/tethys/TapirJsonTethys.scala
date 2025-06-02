package sttp.tapir.json.tethys

import sttp.tapir.Codec.JsonCodec
import sttp.tapir.DecodeResult.Error.JsonDecodeException
import sttp.tapir.DecodeResult.{Error, Value}
import sttp.tapir._
import tethys._
import tethys.jackson._

trait TapirJsonTethys {
  def jsonBody[T: JsonWriter: JsonReader: Schema]: EndpointIO.Body[String, T] = stringBodyUtf8AnyFormat(tethysCodec[T])

  def jsonBodyWithRaw[T: JsonWriter: JsonReader: Schema]: EndpointIO.Body[String, (String, T)] = stringBodyUtf8AnyFormat(
    implicitly[JsonCodec[(String, T)]]
  )

  def jsonQuery[T: JsonWriter: JsonReader: Schema](name: String): EndpointInput.Query[T] =
    queryAnyFormat[T, CodecFormat.Json](name, Codec.jsonQuery(tethysCodec))

  implicit def tethysCodec[T: JsonReader: JsonWriter: Schema]: JsonCodec[T] =
    Codec.json(s =>
      s.jsonAs[T] match {
        case Left(readerError) => Error(s, JsonDecodeException(errors = List.empty, readerError))
        case Right(value)      => Value(value)
      }
    )(_.asJson)
}
