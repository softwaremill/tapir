package sttp.tapir.json.pickler

import sttp.tapir.*
import sttp.tapir.Codec.JsonCodec

/** The bridge that makes a [[Pickler]] usable wherever tapir wants a JSON codec: with it in scope, `jsonBody[T]` from `sttp.tapir` resolves
  * for any `T` that has a `Pickler`.
  */
given picklerToCodec[T](using p: Pickler[T]): JsonCodec[T] = p.toCodec

def jsonBody[T: Pickler]: EndpointIO.Body[String, T] = stringBodyUtf8AnyFormat(summon[Pickler[T]].toCodec)

def jsonBodyWithRaw[T: Pickler]: EndpointIO.Body[String, (String, T)] = stringBodyUtf8AnyFormat(
  Codec.tupledWithRaw(summon[Pickler[T]].toCodec)
)

def jsonQuery[T: Pickler](name: String): EndpointInput.Query[T] =
  queryAnyFormat[T, CodecFormat.Json](name, Codec.jsonQuery(summon[Pickler[T]].toCodec))
