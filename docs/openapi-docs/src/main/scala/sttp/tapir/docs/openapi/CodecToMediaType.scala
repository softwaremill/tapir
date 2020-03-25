package sttp.tapir.docs.openapi

import sttp.tapir.docs.openapi.schema.ObjectSchemas
import sttp.tapir.openapi.{MediaType => OMediaType}
import sttp.tapir.{CodecFormat, _}

import scala.collection.immutable.ListMap

private[openapi] class CodecToMediaType(objectSchemas: ObjectSchemas) {
  def apply[T, CF <: CodecFormat](o: Codec[_, T, CF], example: Option[T]): ListMap[String, OMediaType] = {
    ListMap(
      o.format.getOrElse(CodecFormat.OctetStream()).mediaType.noCharset.toString -> OMediaType(
        Some(objectSchemas(o)),
        example.flatMap(exampleValue(o, _)),
        ListMap.empty,
        ListMap.empty
      )
    )
  }
}
