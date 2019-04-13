package tapir.docs.openapi

import tapir.docs.openapi.schema.SchemaKey
import tapir.openapi.OpenAPI.ReferenceOr
import tapir.openapi.{Schema => OSchema, _}

import scala.collection.immutable.ListMap

private[openapi] class EndpointToOpenApiComponents(keyToSchema: ListMap[SchemaKey, ReferenceOr[OSchema]],
                                                   securitySchemes: SecuritySchemes) {
  def components: Option[Components] = {
    if (keyToSchema.nonEmpty || securitySchemes.nonEmpty)
      Some(Components(keyToSchema, securitySchemes.values.toMap.mapValues(Right(_)).toListMap))
    else None
  }
}
