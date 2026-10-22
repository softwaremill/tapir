package sttp.tapir.json.pickler

import com.github.plokhotnyuk.jsoniter_scala.core.ReaderConfig
import sttp.tapir.*
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.json.pickler.Pickler.defaultReaderConfig

/** The bridge that makes a [[Pickler]] usable wherever tapir wants a JSON codec: with it in scope, `jsonBody[T]` from `sttp.tapir` resolves
  * for any `T` that has a `Pickler`.
  */
given picklerToCodec[T](using p: Pickler[T])(using rc: ReaderConfig = defaultReaderConfig): JsonCodec[T] = p.toCodec

def jsonBody[T](using p: Pickler[T])(using rc: ReaderConfig = defaultReaderConfig): EndpointIO.Body[String, T] =
  stringBodyUtf8AnyFormat(p.toCodec)

def jsonBodyWithRaw[T](using p: Pickler[T])(using rc: ReaderConfig = defaultReaderConfig): EndpointIO.Body[String, (String, T)] =
  stringBodyUtf8AnyFormat(Codec.tupledWithRaw(using p.toCodec))

def jsonQuery[T](name: String)(using p: Pickler[T])(using rc: ReaderConfig = defaultReaderConfig): EndpointInput.Query[T] =
  queryAnyFormat[T, CodecFormat.Json](name, Codec.jsonQuery(p.toCodec))
