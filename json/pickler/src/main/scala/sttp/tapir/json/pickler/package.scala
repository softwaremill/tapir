package sttp.tapir.json.pickler

import sttp.tapir._

def jsonBody[T: Pickler]: EndpointIO.Body[String, T] = stringBodyUtf8AnyFormat(summon[Pickler[T]].toCodec)

def jsonBodyWithRaw[T: Pickler]: EndpointIO.Body[String, (String, T)] = stringBodyUtf8AnyFormat(
  Codec.tupledWithRaw(summon[Pickler[T]].toCodec)
)

def jsonQuery[T: Pickler](name: String): EndpointInput.Query[T] =
  queryAnyFormat[T, CodecFormat.Json](name, Codec.jsonQuery(summon[Pickler[T]].toCodec))
