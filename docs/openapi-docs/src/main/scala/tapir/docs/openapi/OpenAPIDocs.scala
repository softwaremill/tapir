package tapir.docs.openapi
import tapir.Endpoint
import tapir.openapi.OpenAPI

trait OpenAPIDocs {
  implicit class RichOpenAPIEndpoint[I, E, O, S](e: Endpoint[I, E, O, S]) {
    def toOpenAPI(title: String, version: String)(implicit options: OpenAPIDocsOptions): OpenAPI =
      EndpointToOpenAPIDocs.toOpenAPI(title, version, Seq(e), options)
  }

  implicit class RichOpenAPIEndpoints(es: Iterable[Endpoint[_, _, _, _]]) {
    def toOpenAPI(title: String, version: String)(implicit options: OpenAPIDocsOptions): OpenAPI =
      EndpointToOpenAPIDocs.toOpenAPI(title, version, es, options)
  }
}
