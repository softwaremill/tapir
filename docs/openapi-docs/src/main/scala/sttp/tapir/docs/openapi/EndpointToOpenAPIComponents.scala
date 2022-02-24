package sttp.tapir.docs.openapi

import sttp.tapir.apispec.ReferenceOr
import sttp.tapir.apispec.{Schema => ASchema}
import sttp.tapir.docs.apispec.SecuritySchemes
import sttp.tapir.docs.apispec.schema.ObjectKey
import sttp.tapir.internal.{SortListMap, IterableToListMap}
import sttp.tapir.openapi.Components

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
