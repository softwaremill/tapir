package sttp.tapir.docs.openapi

import sttp.tapir.docs.openapi.schema.SchemaKey
import sttp.tapir.apispec.ReferenceOr
import sttp.tapir.apispec.{Schema => ASchema}
import sttp.tapir.openapi.Components

import scala.collection.immutable.ListMap

private[openapi] class EndpointToOpenApiComponents(
    keyToSchema: ListMap[SchemaKey, ReferenceOr[ASchema]],
    securitySchemes: SecuritySchemes
) {
  def components: Option[Components] = {
    if (keyToSchema.nonEmpty || securitySchemes.nonEmpty)
      Some(Components(keyToSchema, securitySchemes.values.toMap.mapValues(Right(_)).toListMap))
    else None
  }
}
