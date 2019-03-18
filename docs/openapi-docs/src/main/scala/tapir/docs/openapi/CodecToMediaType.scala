package tapir.docs.openapi

import tapir.docs.openapi.schema.ObjectSchemas
import tapir.openapi.{MediaType => OMediaType, _}
import tapir.{MediaType => SMediaType, Schema => SSchema, _}

private[openapi] class CodecToMediaType(objectSchemas: ObjectSchemas) {
  def apply[T, M <: SMediaType](o: CodecForOptional[T, M, _],
                                example: Option[T],
                                overrideSchema: Option[SSchema]): Map[String, OMediaType] = {
    val schema = overrideSchema.getOrElse(o.meta.schema)
    Map(
      o.meta.mediaType.mediaTypeNoParams -> OMediaType(Some(objectSchemas(schema)),
                                                       example.flatMap(exampleValue(o, _)),
                                                       Map.empty,
                                                       Map.empty))
  }

  def apply[M <: SMediaType](schema: SSchema, mediaType: M, example: Option[String]): Map[String, OMediaType] = {
    Map(mediaType.mediaTypeNoParams -> OMediaType(Some(objectSchemas(schema)), example.map(ExampleValue), Map.empty, Map.empty))
  }
}
