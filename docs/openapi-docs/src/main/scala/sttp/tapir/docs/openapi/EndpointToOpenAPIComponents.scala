package sttp.tapir.docs.openapi

import sttp.apispec.ReferenceOr
import sttp.apispec.{Schema => ASchema}
import sttp.apispec.openapi.Components
import sttp.tapir.docs.apispec.SecuritySchemes
import sttp.tapir.docs.apispec.schema.SchemaId
import sttp.tapir.internal.{IterableToListMap, SortListMap}

import scala.collection.immutable.ListMap

private[openapi] class EndpointToOpenAPIComponents(
    idToSchema: ListMap[SchemaId, ReferenceOr[ASchema]],
    securitySchemes: SecuritySchemes
) {
  def components: Option[Components] = {
    if (idToSchema.nonEmpty || securitySchemes.nonEmpty) {
      val sortedKeyToSchema = idToSchema.sortByKey
      val sortedSecuritySchemes = securitySchemes.values.toMap.mapValues(Right(_)).toListMap.sortByKey
      Some(Components(schemas = sortedKeyToSchema, securitySchemes = sortedSecuritySchemes))
    } else None
  }
}
