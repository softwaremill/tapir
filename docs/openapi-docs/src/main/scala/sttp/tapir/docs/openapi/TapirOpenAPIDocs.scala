package sttp.tapir.docs.openapi

import sttp.tapir.Endpoint
import sttp.tapir.openapi.{Info, OpenAPI}
import sttp.tapir.server.ServerEndpoint

@deprecated("Use OpenAPIDocsInterpreter", since = "0.17.1")
trait TapirOpenAPIDocs {
  implicit class RichOpenAPIEndpoint[I, E, O, S](e: Endpoint[I, E, O, S]) {
    @deprecated("Use OpenAPIDocsInterpreter.fromEndpoint", since = "0.17.1")
    def toOpenAPI(title: String, version: String)(implicit options: OpenAPIDocsOptions): OpenAPI =
      OpenAPIDocsInterpreter.fromEndpoint(e, Info(title, version))

    @deprecated("Use OpenAPIDocsInterpreter.fromEndpoint", since = "0.17.1")
    def toOpenAPI(info: Info)(implicit options: OpenAPIDocsOptions): OpenAPI =
      OpenAPIDocsInterpreter.fromEndpoint(e, info)
  }

  implicit class RichOpenAPIEndpoints(es: Iterable[Endpoint[_, _, _, _]]) {
    @deprecated("Use OpenAPIDocsInterpreter.fromEndpoints", since = "0.17.1")
    def toOpenAPI(title: String, version: String)(implicit options: OpenAPIDocsOptions): OpenAPI =
      OpenAPIDocsInterpreter.fromEndpoints(es, Info(title, version))

    @deprecated("Use OpenAPIDocsInterpreter.fromEndpoints", since = "0.17.1")
    def toOpenAPI(info: Info)(implicit options: OpenAPIDocsOptions): OpenAPI =
      OpenAPIDocsInterpreter.fromEndpoints(es, info)
  }

  implicit class RichOpenAPIServerEndpoints[F[_]](serverEndpoints: Iterable[ServerEndpoint[_, _, _, _, F]]) {
    @deprecated("Use OpenAPIDocsInterpreter.fromServerEndpoints", since = "0.17.1")
    def toOpenAPI(title: String, version: String)(implicit options: OpenAPIDocsOptions): OpenAPI =
      OpenAPIDocsInterpreter.fromServerEndpoints(serverEndpoints, Info(title, version))

    @deprecated("Use OpenAPIDocsInterpreter.fromServerEndpoints", since = "0.17.1")
    def toOpenAPI(info: Info)(implicit options: OpenAPIDocsOptions): OpenAPI =
      OpenAPIDocsInterpreter.fromServerEndpoints(serverEndpoints, info)
  }
}
