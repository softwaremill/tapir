package sttp.tapir.docs.openapi

import sttp.apispec.ReferenceOr
import sttp.apispec.{Schema => ASchema}
import sttp.apispec.openapi.Components
import sttp.tapir.docs.apispec.SecuritySchemes
import sttp.tapir.docs.apispec.schema.ObjectKey
import sttp.tapir.internal.{SortListMap, IterableToListMap}

import scala.collection.immutable.ListMap

private[openapi] class EndpointToOpenAPIComponents(
    keyToSchema: ListMap[ObjectKey, ReferenceOr[ASchema]],
    securitySchemes: SecuritySchemes
) {
  def components: Option[Components] = {
    if (keyToSchema.nonEmpty || securitySchemes.nonEmpty) {
      val sortedKeyToSchema = keyToSchema.sortByKey
      val sortedSecuritySchemes = securitySchemes.values.toMap.mapValues(Right(_)).toListMap.sortByKey
      Some(Components(schemas = sortedKeyToSchema, securitySchemes = sortedSecuritySchemes))
    } else None
  }
}
