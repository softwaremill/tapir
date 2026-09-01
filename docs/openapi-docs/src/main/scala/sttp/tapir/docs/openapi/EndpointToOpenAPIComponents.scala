package sttp.tapir.docs.openapi

import sttp.apispec.{Schema => ASchema}
import sttp.apispec.openapi.{Components, Header, Parameter, ReferenceOr}
import sttp.tapir.docs.apispec.SecuritySchemes
import sttp.tapir.docs.apispec.schema.SchemaId
import sttp.tapir.internal.{IterableToListMap, SortListMap}

import scala.collection.immutable.ListMap

private[openapi] class EndpointToOpenAPIComponents(
    idToSchema: ListMap[SchemaId, ASchema],
    securitySchemes: SecuritySchemes,
    reusableComponents: ReusableComponents
) {
  def components: Option[Components] = {
    if (idToSchema.nonEmpty || securitySchemes.nonEmpty || reusableComponents.nonEmpty) {
      val sortedKeyToSchema = idToSchema.sortByKey
      val sortedSecuritySchemes = securitySchemes.values.toMap.mapValues(Right(_)).toListMap.sortByKey
      val sortedParameters: ListMap[String, ReferenceOr[Parameter]] =
        reusableComponents.parameterToName.map { case (parameter, name) => name -> Right(parameter) }.toListMap.sortByKey
      val sortedHeaders: ListMap[String, ReferenceOr[Header]] =
        reusableComponents.headerToName.map { case ((_, header), name) => name -> Right(header) }.toListMap.sortByKey
      Some(
        Components(
          schemas = sortedKeyToSchema,
          parameters = sortedParameters,
          headers = sortedHeaders,
          securitySchemes = sortedSecuritySchemes
        )
      )
    } else None
  }
}
