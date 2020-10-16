package sttp.tapir.docs.asyncapi

import sttp.tapir.apispec.{ReferenceOr, Schema => ASchema}
import sttp.tapir.asyncapi.Components
import sttp.tapir.docs.apispec.SecuritySchemes
import sttp.tapir.docs.apispec.schema.SchemaKey

import scala.collection.immutable.ListMap

private[asyncapi] class EndpointToAsyncAPIComponents(
    keyToSchema: ListMap[SchemaKey, ReferenceOr[ASchema]],
    securitySchemes: SecuritySchemes
) {
  def components: Option[Components] = {
    if (keyToSchema.nonEmpty || securitySchemes.nonEmpty)
      Some(
        Components(
          keyToSchema,
          ListMap.empty,
          securitySchemes.values.toMap.mapValues(Right(_)).toListMap,
          ListMap.empty,
          ListMap.empty,
          ListMap.empty,
          ListMap.empty
        )
      )
    else None
  }
}
