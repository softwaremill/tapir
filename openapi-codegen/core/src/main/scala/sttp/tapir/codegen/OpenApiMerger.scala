package sttp.tapir.codegen

import sttp.tapir.codegen.openapi.models.OpenapiComponent
import sttp.tapir.codegen.openapi.models.OpenapiModels.OpenapiDocument

object OpenApiMerger {

  def merge(docs: Seq[OpenapiDocument]): OpenapiDocument = {
    require(docs.nonEmpty, "Cannot merge an empty sequence of OpenAPI documents")
    docs.tail.foldLeft(docs.head)(mergeTwo)
  }

  private def mergeTwo(left: OpenapiDocument, right: OpenapiDocument): OpenapiDocument = {
    val mergedPaths = left.paths ++ right.paths
    val pathUrls = mergedPaths.map(_.url)
    val duplicatePaths = pathUrls.diff(pathUrls.distinct).distinct
    if (duplicatePaths.nonEmpty)
      throw new IllegalArgumentException(s"Duplicate paths when merging OpenAPI documents: ${duplicatePaths.mkString(", ")}")

    val mergedComponents = (left.components, right.components) match {
      case (None, None)       => None
      case (Some(l), None)    => Some(l)
      case (None, Some(r))    => Some(r)
      case (Some(l), Some(r)) => Some(mergeComponents(l, r))
    }

    left.copy(
      servers = if (left.servers.nonEmpty) left.servers else right.servers,
      paths = mergedPaths,
      components = mergedComponents,
      security = if (left.security.nonEmpty) left.security else right.security
    )
  }

  private def mergeComponents(left: OpenapiComponent, right: OpenapiComponent): OpenapiComponent = {
    val mergedSchemas = left.schemas ++ right.schemas.filterNot { case (k, _) => left.schemas.contains(k) }
    val conflictingSchemas = left.schemas.keySet.intersect(right.schemas.keySet).filter { k =>
      left.schemas(k) != right.schemas(k)
    }
    if (conflictingSchemas.nonEmpty)
      throw new IllegalArgumentException(
        s"Conflicting schema definitions when merging OpenAPI documents: ${conflictingSchemas.mkString(", ")}"
      )

    val mergedSecuritySchemes = left.securitySchemes ++ right.securitySchemes.filterNot { case (k, _) =>
      left.securitySchemes.contains(k)
    }
    val mergedParameters = left.parameters ++ right.parameters.filterNot { case (k, _) => left.parameters.contains(k) }
    val mergedResponses = left.responses ++ right.responses.filterNot { case (k, _) => left.responses.contains(k) }
    val mergedRequestBodies = left.requestBodies ++ right.requestBodies.filterNot { case (k, _) =>
      left.requestBodies.contains(k)
    }
    val mergedHeaders = left.headers ++ right.headers.filterNot { case (k, _) => left.headers.contains(k) }
    // as for schemas, and unlike the other component maps: a header definition determines the generated endpoint's type, so silently
    // keeping the left one would generate the wrong type for the right document's endpoints
    val conflictingHeaders = left.headers.keySet.intersect(right.headers.keySet).filter { k =>
      left.headers(k) != right.headers(k)
    }
    if (conflictingHeaders.nonEmpty)
      throw new IllegalArgumentException(
        s"Conflicting header definitions when merging OpenAPI documents: ${conflictingHeaders.mkString(", ")}"
      )

    OpenapiComponent(mergedSchemas, mergedSecuritySchemes, mergedParameters, mergedResponses, mergedRequestBodies, mergedHeaders)
  }
}
