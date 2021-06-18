package sttp.tapir.docs.openapi

import sttp.tapir.Endpoint
import sttp.tapir.openapi.{Info, OpenAPI}
import sttp.tapir.server.ServerEndpoint

@deprecated("Use OpenAPIDocsInterpreter", since = "0.17.1")
trait TapirOpenAPIDocs {
  implicit class RichOpenAPIEndpoint[I, E, O, S](e: Endpoint[I, E, O, S]) {
    @deprecated("Use OpenAPIDocsInterpreter().toOpenAPI", since = "0.17.1")
    def toOpenAPI(title: String, version: String)(implicit options: OpenAPIDocsOptions): OpenAPI =
      OpenAPIDocsInterpreter().toOpenAPI(e, Info(title, version), List.empty)

    @deprecated("Use OpenAPIDocsInterpreter().toOpenAPI", since = "0.17.1")
    def toOpenAPI(info: Info)(implicit options: OpenAPIDocsOptions): OpenAPI =
      OpenAPIDocsInterpreter().toOpenAPI(e, info, List.empty)
  }

  implicit class RichOpenAPIEndpoints(es: Iterable[Endpoint[_, _, _, _]]) {
    @deprecated("Use OpenAPIDocsInterpreter().toOpenAPI", since = "0.17.1")
    def toOpenAPI(title: String, version: String)(implicit options: OpenAPIDocsOptions): OpenAPI =
      OpenAPIDocsInterpreter().toOpenAPI(es, Info(title, version), List.empty)

    @deprecated("Use OpenAPIDocsInterpreter().toOpenAPI", since = "0.17.1")
    def toOpenAPI(info: Info)(implicit options: OpenAPIDocsOptions): OpenAPI =
      OpenAPIDocsInterpreter().toOpenAPI(es, info, List.empty)
  }

  implicit class RichOpenAPIServerEndpoints[F[_]](serverEndpoints: Iterable[ServerEndpoint[_, _, _, _, F]]) {
    @deprecated("Use OpenAPIDocsInterpreter().serverEndpointsToOpenAPI", since = "0.17.1")
    def toOpenAPI(title: String, version: String)(implicit options: OpenAPIDocsOptions): OpenAPI =
      OpenAPIDocsInterpreter().serverEndpointsToOpenAPI(serverEndpoints, Info(title, version), List.empty)

    @deprecated("Use OpenAPIDocsInterpreter().serverEndpointsToOpenAPI", since = "0.17.1")
    def toOpenAPI(info: Info)(implicit options: OpenAPIDocsOptions): OpenAPI =
      OpenAPIDocsInterpreter().serverEndpointsToOpenAPI(serverEndpoints, info, List.empty)
  }
}
