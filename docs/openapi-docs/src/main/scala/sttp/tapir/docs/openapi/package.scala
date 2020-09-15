package sttp.tapir.docs

import sttp.tapir.openapi.{ExampleMultipleValue, ExampleSingleValue, ExampleValue, SecurityScheme}
import sttp.tapir.{Codec, EndpointInput, SchemaType}

package object openapi extends TapirOpenAPIDocs {
  private[openapi] type SchemeName = String
  private[openapi] type SecuritySchemes = Map[EndpointInput.Auth[_], (SchemeName, SecurityScheme)]

  private[openapi] def uniqueName(base: String, isUnique: String => Boolean): String = {
    var i = 0
    var result = base
    while (!isUnique(result)) {
      i += 1
      result = base + i
    }
    result
  }

  private[openapi] def rawToString[T](v: Any): String = v.toString
  private[openapi] def encodeToString[T](codec: Codec[_, T, _]): T => Option[String] = e => Some(rawToString(codec.encode(e)))

  private[openapi] def exampleValue[T](v: String): ExampleValue = ExampleSingleValue(v)
  private[openapi] def exampleValue[T](codec: Codec[_, T, _], e: T): Option[ExampleValue] = {
    (codec.encode(e), codec.schema.map(_.schemaType)) match {
      case (it: Iterable[_], Some(_: SchemaType.SArray)) => Some(ExampleMultipleValue(it.map(rawToString).toList))
      case (it: Iterable[_], _)                          => it.headOption.map(v => ExampleSingleValue(rawToString(v)))
      case (it: Option[_], _)                            => it.map(v => ExampleSingleValue(rawToString(v)))
      case (v, _)                                        => Some(ExampleSingleValue(rawToString(v)))
    }
  }
}
