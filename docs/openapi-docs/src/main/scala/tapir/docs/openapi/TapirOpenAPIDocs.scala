package tapir.docs.openapi
import tapir.Endpoint
import tapir.openapi.{Info, OpenAPI}
import tapir.server.ServerEndpoint

trait TapirOpenAPIDocs {
  implicit class RichOpenAPIEndpoint[I, E, O, S](e: Endpoint[I, E, O, S]) {
    def toOpenAPI(title: String, version: String)(implicit options: OpenAPIDocsOptions): OpenAPI = toOpenAPI(Info(title, version))

    def toOpenAPI(info: Info)(implicit options: OpenAPIDocsOptions): OpenAPI =
      EndpointToOpenAPIDocs.toOpenAPI(info, Seq(e), options)
  }

  implicit class RichOpenAPIEndpoints(es: Iterable[Endpoint[_, _, _, _]]) {
    def toOpenAPI(title: String, version: String)(implicit options: OpenAPIDocsOptions): OpenAPI = toOpenAPI(Info(title, version))

    def toOpenAPI(info: Info)(implicit options: OpenAPIDocsOptions): OpenAPI =
      EndpointToOpenAPIDocs.toOpenAPI(info, es, options)
  }

  implicit class RichOpenAPIServerEndpoints[F[_]](serverEndpoints: Iterable[ServerEndpoint[_, _, _, _, F]]) {
    def toOpenAPI(title: String, version: String)(implicit options: OpenAPIDocsOptions): OpenAPI = toOpenAPI(Info(title, version))

    def toOpenAPI(info: Info)(implicit options: OpenAPIDocsOptions): OpenAPI =
      EndpointToOpenAPIDocs.toOpenAPI(info, serverEndpoints.map(_.endpoint), options)
  }
}
