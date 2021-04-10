package sttp.tapir.docs.openapi

import sttp.tapir.Endpoint
import sttp.tapir.openapi.{Info, OpenAPI}
import sttp.tapir.server.ServerEndpoint

trait OpenAPIDocsInterpreter {
  def toOpenAPI[I, E, O, R](e: Endpoint[I, E, O, R], title: String, version: String)(implicit options: OpenAPIDocsOptions): OpenAPI =
    toOpenAPI(e, Info(title, version))

  def toOpenAPI[I, E, O, R](e: Endpoint[I, E, O, R], info: Info)(implicit options: OpenAPIDocsOptions): OpenAPI =
    EndpointToOpenAPIDocs.toOpenAPI(info, Seq(e), options)

  def toOpenAPI[R, F[_]](se: ServerEndpoint[R, F], title: String, version: String)(implicit
      options: OpenAPIDocsOptions
  ): OpenAPI = toOpenAPI(se.endpoint, Info(title, version))

  def toOpenAPI[R, F[_]](se: ServerEndpoint[R, F], info: Info)(implicit options: OpenAPIDocsOptions): OpenAPI =
    EndpointToOpenAPIDocs.toOpenAPI(info, Seq(se.endpoint), options)

  def toOpenAPI(es: Iterable[Endpoint[_, _, _, _]], title: String, version: String)(implicit options: OpenAPIDocsOptions): OpenAPI =
    toOpenAPI(es, Info(title, version))

  def toOpenAPI(es: Iterable[Endpoint[_, _, _, _]], info: Info)(implicit options: OpenAPIDocsOptions): OpenAPI =
    EndpointToOpenAPIDocs.toOpenAPI(info, es, options)

  def serverEndpointsToOpenAPI[F[_]](ses: Iterable[ServerEndpoint[_, F]], title: String, version: String)(implicit
      options: OpenAPIDocsOptions
  ): OpenAPI = serverEndpointsToOpenAPI(ses, Info(title, version))

  def serverEndpointsToOpenAPI[F[_]](ses: Iterable[ServerEndpoint[_, F]], info: Info)(implicit
      options: OpenAPIDocsOptions
  ): OpenAPI =
    EndpointToOpenAPIDocs.toOpenAPI(info, ses.map(_.endpoint), options)
}

object OpenAPIDocsInterpreter extends OpenAPIDocsInterpreter
