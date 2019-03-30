package tapir.docs.openapi

import tapir.docs.openapi.schema.ObjectSchemasForEndpoints
import tapir.openapi._
import tapir.{EndpointInput, _}

import scala.collection.immutable.ListMap

object EndpointToOpenAPIDocs {
  def toOpenAPI(api: Info, es: Iterable[Endpoint[_, _, _, _]], options: OpenAPIDocsOptions): OpenAPI = {
    val es2 = es.map(nameAllPathCapturesInEndpoint)
    val objectSchemas = ObjectSchemasForEndpoints(es2)
    val securitySchemes = SecuritySchemesForEndpoints(es2)
    val pathCreator = new EndpointToOpenApiPaths(objectSchemas, securitySchemes, options)
    val componentsCreator = new EndpointToOpenApiComponents(objectSchemas, securitySchemes)

    val base = apiToOpenApi(api, componentsCreator)

    es2.map(pathCreator.pathItem).foldLeft(base) {
      case (current, (path, pathItem)) =>
        current.addPathItem(path, pathItem)
    }
  }

  private def apiToOpenApi(info: Info, componentsCreator: EndpointToOpenApiComponents): OpenAPI = {
    OpenAPI(
      info = info,
      servers = List.empty,
      paths = ListMap.empty,
      components = componentsCreator.components,
      security = List.empty
    )
  }

  private def nameAllPathCapturesInEndpoint(e: Endpoint[_, _, _, _]): Endpoint[_, _, _, _] = {
    val (input2, _) = new EndpointInputMapper[Int](
      {
        case (EndpointInput.PathCapture(codec, None, info), i) =>
          (EndpointInput.PathCapture(codec, Some(s"p$i"), info), i + 1)
      },
      PartialFunction.empty
    ).mapInput(e.input, 1)

    e.copy(input = input2)
  }
}
