package tapir.docs.openapi

import tapir.Api.{Contact, License}
import tapir.docs.openapi.schema.ObjectSchemasForEndpoints
import tapir.openapi.{Contact => OpenApiContact, License => OpenApiLicense, _}
import tapir.{EndpointInput, _}

object EndpointToOpenAPIDocs {
  def toOpenAPI(api: Api, es: Iterable[Endpoint[_, _, _, _]], options: OpenAPIDocsOptions): OpenAPI = {
    val es2 = es.map(nameAllPathCapturesInEndpoint)
    val objectSchemas = ObjectSchemasForEndpoints(es2)
    val pathCreator = new EndpointToOpenApiPaths(objectSchemas, options)
    val componentsCreator = new EndpointToOpenApiComponents(objectSchemas)

    val base = apiToOpenApi(api, componentsCreator)

    es2.map(pathCreator.pathItem).foldLeft(base) {
      case (current, (path, pathItem)) =>
        current.addPathItem(path, pathItem)
    }
  }

  private def apiToOpenApi(api: Api, componentsCreator: EndpointToOpenApiComponents): OpenAPI = {
    OpenAPI(
      info = toOpenAPI(api),
      servers = List.empty,
      paths = Map.empty,
      components = componentsCreator.components
    )
  }

  private def toOpenAPI(api: Api): Info = {
    Info(
      api.info.title,
      api.info.version,
      api.info.description,
      api.info.termsOfService,
      api.info.contact.map(toOpenAPI),
      api.info.license.map(toOpenAPI)
    )
  }

  private def toOpenAPI(contact: Contact): OpenApiContact = {
    OpenApiContact(contact.name, contact.email, contact.url)
  }

  private def toOpenAPI(license: License): OpenApiLicense = {
    OpenApiLicense(license.name, license.url)
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
