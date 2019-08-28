package tapir.docs

import tapir.{Codec, CodecForMany, CodecForOptional, EndpointInput}
import tapir.openapi.{ExampleValue, SecurityScheme}

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

  /**
    * Used to encode examples and enum values
    */
  type EncodeToAny[T] = T => Option[Any]

  private def encodeValue[T](v: Any): Any = v.toString
  private[openapi] def encodeAny[T](codec: Codec[T, _, _]): EncodeToAny[T] = e => Some(encodeValue(codec.encode(e)))
  private[openapi] def encodeAny[T](codec: CodecForOptional[T, _, _]): EncodeToAny[T] = e => codec.encode(e).map(encodeValue)
  private[openapi] def encodeAny[T](codec: CodecForMany[T, _, _]): EncodeToAny[T] = e => codec.encode(e).headOption.map(encodeValue)

  private[openapi] def exampleValue[T](v: Any): ExampleValue = ExampleValue(v.toString)
  private[openapi] def exampleValue[T](codec: Codec[T, _, _], e: T): Option[ExampleValue] = encodeAny(codec)(e).map(exampleValue)
  private[openapi] def exampleValue[T](codec: CodecForOptional[T, _, _], e: T): Option[ExampleValue] = encodeAny(codec)(e).map(exampleValue)
  private[openapi] def exampleValue[T](codec: CodecForMany[T, _, _], e: T): Option[ExampleValue] = encodeAny(codec)(e).map(exampleValue)
}
