package sttp.tapir.docs.openapi

import sttp.tapir._
import sttp.tapir.internal._
import sttp.tapir.docs.apispec.schema.SchemasForEndpoints
import sttp.tapir.docs.apispec.{SecuritySchemesForEndpoints, nameAllPathCapturesInEndpoint}
import sttp.tapir.openapi._

import scala.collection.immutable.ListMap

private[openapi] object EndpointToOpenAPIDocs {
  def toOpenAPI(api: Info, es: Iterable[Endpoint[_, _, _, _]], options: OpenAPIDocsOptions): OpenAPI = {
    val es2 = es.filter(e => findWebSocket(e).isEmpty).map(nameAllPathCapturesInEndpoint)
    val (keyToSchema, schemas) = SchemasForEndpoints(es2, options.schemaObjectInfoToNameMapper)
    val securitySchemes = SecuritySchemesForEndpoints(es2)
    val pathCreator = new EndpointToOpenAPIPaths(schemas, securitySchemes, options)
    val componentsCreator = new EndpointToOpenAPIComponents(keyToSchema, securitySchemes)

    val base = apiToOpenApi(api, componentsCreator)

    es2.map(pathCreator.pathItem).foldLeft(base) { case (current, (path, pathItem)) =>
      current.addPathItem(path, pathItem)
    }
  }

  private def apiToOpenApi(info: Info, componentsCreator: EndpointToOpenAPIComponents): OpenAPI = {
    OpenAPI(
      info = info,
      tags = List.empty,
      servers = List.empty,
      paths = ListMap.empty,
      components = componentsCreator.components,
      security = List.empty
    )
  }
}
