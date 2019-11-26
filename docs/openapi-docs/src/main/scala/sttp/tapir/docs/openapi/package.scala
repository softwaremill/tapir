package sttp.tapir.docs

import sttp.tapir.openapi.{ExampleMultipleValue, ExampleSingleValue, ExampleValue, SecurityScheme}
import sttp.tapir.{Codec, CodecForMany, CodecForOptional, EndpointInput, SchemaType}

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
  private[openapi] def encodeToString[T](codec: Codec[T, _, _]): T => Option[String] = e => Some(rawToString(codec.encode(e)))
  private[openapi] def encodeToString[T](codec: CodecForOptional[T, _, _]): T => Option[String] = e => codec.encode(e).map(rawToString)
  private[openapi] def encodeToString[T](codec: CodecForMany[T, _, _]): T => List[String] =
    e => codec.encode(e).map(rawToString).toList

  private[openapi] def exampleValue[T](v: String): ExampleValue = ExampleSingleValue(v)
  private[openapi] def exampleValue[T](codec: Codec[T, _, _], e: T): Option[ExampleValue] = encodeToString(codec)(e).map(exampleValue)
  private[openapi] def exampleValue[T](codec: CodecForOptional[T, _, _], e: T): Option[ExampleValue] =
    encodeToString(codec)(e).map(exampleValue)

  private[openapi] def exampleValue[T](codec: CodecForMany[T, _, _], e: T): Option[ExampleValue] = {
    val encodedValues = encodeToString(codec)(e)
    codec.meta.schema.schemaType match {
      case SchemaType.SArray(_) => Some(ExampleMultipleValue(encodedValues))
      case _                    => encodedValues.headOption.map(ExampleSingleValue)
    }
  }
}
