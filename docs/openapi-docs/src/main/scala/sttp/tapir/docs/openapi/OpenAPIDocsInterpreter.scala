package sttp.tapir.docs.openapi

import sttp.tapir.Endpoint
import sttp.tapir.openapi.{Info, OpenAPI}
import sttp.tapir.server.ServerEndpoint

trait OpenAPIDocsInterpreter {
  def fromEndpoint[I, E, O, S](e: Endpoint[I, E, O, S], title: String, version: String)(implicit options: OpenAPIDocsOptions): OpenAPI =
    fromEndpoint(e, Info(title, version))

  def fromEndpoint[I, E, O, S](e: Endpoint[I, E, O, S], info: Info)(implicit options: OpenAPIDocsOptions): OpenAPI =
    EndpointToOpenAPIDocs.toOpenAPI(info, Seq(e), options)

  def fromServerEndpoint[I, E, O, S, F[_]](se: ServerEndpoint[I, E, O, S, F], title: String, version: String)(implicit
      options: OpenAPIDocsOptions
  ): OpenAPI =
    fromEndpoint(se.endpoint, Info(title, version))

  def fromServerEndpoint[I, E, O, S, F[_]](se: ServerEndpoint[I, E, O, S, F], info: Info)(implicit options: OpenAPIDocsOptions): OpenAPI =
    EndpointToOpenAPIDocs.toOpenAPI(info, Seq(se.endpoint), options)

  def fromEndpoints(es: Iterable[Endpoint[_, _, _, _]], title: String, version: String)(implicit options: OpenAPIDocsOptions): OpenAPI =
    fromEndpoints(es, Info(title, version))

  def fromEndpoints(es: Iterable[Endpoint[_, _, _, _]], info: Info)(implicit options: OpenAPIDocsOptions): OpenAPI =
    EndpointToOpenAPIDocs.toOpenAPI(info, es, options)

  def fromServerEndpoints[F[_]](ses: Iterable[ServerEndpoint[_, _, _, _, F]], title: String, version: String)(implicit
      options: OpenAPIDocsOptions
  ): OpenAPI = fromServerEndpoints(ses, Info(title, version))

  def fromServerEndpoints[F[_]](ses: Iterable[ServerEndpoint[_, _, _, _, F]], info: Info)(implicit options: OpenAPIDocsOptions): OpenAPI =
    EndpointToOpenAPIDocs.toOpenAPI(info, ses.map(_.endpoint), options)
}

object OpenAPIDocsInterpreter extends OpenAPIDocsInterpreter
