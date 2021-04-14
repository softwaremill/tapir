package sttp.tapir.docs.openapi

import sttp.tapir._
import sttp.tapir.docs.apispec.schema.{SchemasForEndpoints, ToObjectSchema}
import sttp.tapir.docs.apispec.{SecuritySchemesForEndpoints, nameAllPathCapturesInEndpoint}
import sttp.tapir.internal._
import sttp.tapir.openapi._

import scala.collection.immutable.ListMap

private[openapi] object EndpointToOpenAPIDocs {
  def toOpenAPI(api: Info, es: Iterable[Endpoint[_, _, _, _]], options: OpenAPIDocsOptions, extensions: List[Extension[_]]): OpenAPI = {
    val es2 = es.filter(e => findWebSocket(e).isEmpty).map(nameAllPathCapturesInEndpoint)
    val toObjectSchema = new ToObjectSchema(options.referenceEnums)
    val (keyToSchema, schemas) = new SchemasForEndpoints(es2, options.schemaName, toObjectSchema).apply()
    val securitySchemes = SecuritySchemesForEndpoints(es2)
    val pathCreator = new EndpointToOpenAPIPaths(schemas, securitySchemes, options)
    val componentsCreator = new EndpointToOpenAPIComponents(keyToSchema, securitySchemes)

    val base = apiToOpenApi(api, componentsCreator, extensions)

    es2.map(pathCreator.pathItem).foldLeft(base) { case (current, (path, pathItem)) =>
      current.addPathItem(path, pathItem)
    }
  }

  private def apiToOpenApi(info: Info, componentsCreator: EndpointToOpenAPIComponents, extensions: List[Extension[_]]): OpenAPI = {
    OpenAPI(
      info = info,
      tags = List.empty,
      servers = List.empty,
      paths = Paths(ListMap.empty),
      components = componentsCreator.components,
      security = List.empty,
      extensions = Extensions.fromIterable(extensions)
    )
  }
}
