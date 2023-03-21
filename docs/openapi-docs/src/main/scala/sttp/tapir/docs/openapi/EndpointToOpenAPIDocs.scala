package sttp.tapir.docs.openapi

import sttp.apispec.openapi._
import sttp.tapir._
import sttp.tapir.docs.apispec.schema.{SchemasForEndpoints, ToKeyedSchemas}
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
    val toKeyedSchemas = new ToKeyedSchemas
    val additionalOutputs = es2.flatMap(e => options.defaultDecodeFailureOutput(e.input)).toSet.toList
    val (idToSchema, schemas) =
      new SchemasForEndpoints(es2, options.schemaName, toKeyedSchemas, options.markOptionsAsNullable, additionalOutputs).apply()
    val securitySchemes = SecuritySchemesForEndpoints(es2, apiKeyAuthTypeName = "apiKey")
    val pathCreator = new EndpointToOpenAPIPaths(schemas, securitySchemes, options)
    val componentsCreator = new EndpointToOpenAPIComponents(idToSchema, securitySchemes)

    val base = apiToOpenApi(api, componentsCreator, docsExtensions)

    es2.map(pathCreator.pathItem).foldLeft(base) { case (current, (path, pathItem)) =>
      current.addPathItem(path, pathItem)
    }
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
