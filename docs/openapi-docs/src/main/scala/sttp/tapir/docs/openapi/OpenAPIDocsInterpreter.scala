package sttp.tapir.docs.openapi

import sttp.tapir.Endpoint
import sttp.tapir.openapi.{Info, OpenAPI}
import sttp.tapir.server.ServerEndpoint

trait OpenAPIDocsInterpreter {
  def toOpenAPI[I, E, O, S](e: Endpoint[I, E, O, S], title: String, version: String)(implicit options: OpenAPIDocsOptions): OpenAPI =
    toOpenAPI(e, Info(title, version))

  def toOpenAPI[I, E, O, S](e: Endpoint[I, E, O, S], info: Info)(implicit options: OpenAPIDocsOptions): OpenAPI =
    EndpointToOpenAPIDocs.toOpenAPI(info, Seq(e), options)

  def toOpenAPI[I, E, O, S, F[_]](se: ServerEndpoint[I, E, O, S, F], title: String, version: String)(implicit
      options: OpenAPIDocsOptions
  ): OpenAPI =
    toOpenAPI(se.endpoint, Info(title, version))

  def toOpenAPI[I, E, O, S, F[_]](se: ServerEndpoint[I, E, O, S, F], info: Info)(implicit options: OpenAPIDocsOptions): OpenAPI =
    EndpointToOpenAPIDocs.toOpenAPI(info, Seq(se.endpoint), options)

  def toOpenAPI(es: Iterable[Endpoint[_, _, _, _]], title: String, version: String)(implicit options: OpenAPIDocsOptions): OpenAPI =
    toOpenAPI(es, Info(title, version))

  def toOpenAPI(es: Iterable[Endpoint[_, _, _, _]], info: Info)(implicit options: OpenAPIDocsOptions): OpenAPI =
    EndpointToOpenAPIDocs.toOpenAPI(info, es, options)

  def serverEndpointsToOpenAPI[F[_]](ses: Iterable[ServerEndpoint[_, _, _, _, F]], title: String, version: String)(implicit
      options: OpenAPIDocsOptions
  ): OpenAPI = serverEndpointsToOpenAPI(ses, Info(title, version))

  def serverEndpointsToOpenAPI[F[_]](ses: Iterable[ServerEndpoint[_, _, _, _, F]], info: Info)(implicit
      options: OpenAPIDocsOptions
  ): OpenAPI =
    EndpointToOpenAPIDocs.toOpenAPI(info, ses.map(_.endpoint), options)
}

object OpenAPIDocsInterpreter extends OpenAPIDocsInterpreter
