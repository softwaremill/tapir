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

  private[openapi] def exampleValue[T](v: Any): ExampleValue = ExampleValue(v.toString)
  private[openapi] def exampleValue[T](codec: Codec[T, _, _], e: T): Option[ExampleValue] = Some(exampleValue(codec.encode(e)))
  private[openapi] def exampleValue[T](codec: CodecForOptional[T, _, _], e: T): Option[ExampleValue] = codec.encode(e).map(exampleValue)
  private[openapi] def exampleValue[T](codec: CodecForMany[T, _, _], e: T): Option[ExampleValue] =
    codec.encode(e).headOption.map(exampleValue)
}
