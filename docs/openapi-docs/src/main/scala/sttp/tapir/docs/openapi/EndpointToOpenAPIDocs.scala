package sttp.tapir.docs.openapi

import sttp.apispec.openapi._
import sttp.tapir._
import sttp.tapir.docs.apispec.schema.SchemasForEndpoints
import sttp.tapir.docs.apispec.{DocsExtension, DocsExtensions, SecuritySchemesForEndpoints, nameAllPathCapturesInEndpoint}
import sttp.tapir.internal._

import scala.collection.immutable.ListMap

private[openapi] object EndpointToOpenAPIDocs {
  def toOpenAPI(
      api: Info,
      es: Iterable[AnyEndpoint],
      options: OpenAPIDocsOptions,
      docsExtensions: List[DocsExtension[_]]
  ): OpenAPI = {
    val es2 = es.filter(e => findWebSocket(e).isEmpty).map(nameAllPathCapturesInEndpoint)
    val additionalOutputs = es2.flatMap(e => options.defaultDecodeFailureOutput(e.input)).toSet.toList
    val (idToSchema, tschemaToASchema) =
      new SchemasForEndpoints(es2, options.schemaName, options.markOptionsAsNullable, options.failOnDuplicateSchemaName, additionalOutputs).apply()
    val securitySchemes = SecuritySchemesForEndpoints(es2, apiKeyAuthTypeName = "apiKey")
    val pathCreator = new EndpointToOpenAPIPaths(tschemaToASchema, securitySchemes, options)
    val componentsCreator = new EndpointToOpenAPIComponents(idToSchema, securitySchemes)

    val base = apiToOpenApi(api, componentsCreator, docsExtensions)

    val spec = es2.map(pathCreator.pathItem).foldLeft(base) { case (current, (path, pathItem)) =>
      current.addPathItem(path, pathItem)
    }

    if (options.failOnDuplicateOperationId) {

      val operationIds = spec.paths.pathItems.flatMap { item =>
        List(item._2.get, item._2.put, item._2.post, item._2.delete, item._2.options, item._2.head, item._2.patch, item._2.trace)
          .flatMap(_.flatMap(_.operationId))
      }

      operationIds
        .groupBy(identity)
        .filter(_._2.size > 1)
        .foreach { case (name, _) => throw new IllegalStateException(s"Duplicate endpoints names found: ${name}") }
    }

    spec
  }

  private def apiToOpenApi(info: Info, componentsCreator: EndpointToOpenAPIComponents, docsExtensions: List[DocsExtension[_]]): OpenAPI = {
    OpenAPI(
      info = info,
      tags = List.empty,
      servers = List.empty,
      paths = Paths(ListMap.empty),
      components = componentsCreator.components,
      security = List.empty,
      extensions = DocsExtensions.fromIterable(docsExtensions)
    )
  }
}
