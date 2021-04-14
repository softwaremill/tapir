package sttp.tapir.docs.openapi

import sttp.tapir.{Endpoint, Extension}
import sttp.tapir.openapi.{Info, OpenAPI}
import sttp.tapir.server.ServerEndpoint

trait OpenAPIDocsInterpreter {
  def toOpenAPI[I, E, O, S](e: Endpoint[I, E, O, S], title: String, version: String)(implicit options: OpenAPIDocsOptions): OpenAPI =
    toOpenAPI(e, Info(title, version), List.empty)

  def toOpenAPI[I, E, O, S](e: Endpoint[I, E, O, S], info: Info, extensions: List[Extension[_]])(implicit
      options: OpenAPIDocsOptions
  ): OpenAPI =
    EndpointToOpenAPIDocs.toOpenAPI(info, Seq(e), options, extensions)

  def toOpenAPI[I, E, O, S, F[_]](se: ServerEndpoint[I, E, O, S, F], title: String, version: String)(implicit
      options: OpenAPIDocsOptions
  ): OpenAPI =
    toOpenAPI(se.endpoint, Info(title, version), List.empty)

  def toOpenAPI[I, E, O, S, F[_]](se: ServerEndpoint[I, E, O, S, F], info: Info, extensions: List[Extension[_]])(implicit
      options: OpenAPIDocsOptions
  ): OpenAPI =
    EndpointToOpenAPIDocs.toOpenAPI(info, Seq(se.endpoint), options, extensions)

  def toOpenAPI(es: Iterable[Endpoint[_, _, _, _]], title: String, version: String)(implicit options: OpenAPIDocsOptions): OpenAPI =
    toOpenAPI(es, Info(title, version), List.empty)

  def toOpenAPI(es: Iterable[Endpoint[_, _, _, _]], info: Info, extensions: List[Extension[_]])(implicit
      options: OpenAPIDocsOptions
  ): OpenAPI =
    EndpointToOpenAPIDocs.toOpenAPI(info, es, options, extensions)

  def serverEndpointsToOpenAPI[F[_]](ses: Iterable[ServerEndpoint[_, _, _, _, F]], title: String, version: String)(implicit
      options: OpenAPIDocsOptions
  ): OpenAPI = serverEndpointsToOpenAPI(ses, Info(title, version), List.empty)

  def serverEndpointsToOpenAPI[F[_]](ses: Iterable[ServerEndpoint[_, _, _, _, F]], info: Info, extensions: List[Extension[_]])(implicit
      options: OpenAPIDocsOptions
  ): OpenAPI =
    EndpointToOpenAPIDocs.toOpenAPI(info, ses.map(_.endpoint), options, extensions)
}

object OpenAPIDocsInterpreter extends OpenAPIDocsInterpreter
