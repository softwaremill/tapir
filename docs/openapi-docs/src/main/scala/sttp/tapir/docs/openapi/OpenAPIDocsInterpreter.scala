package sttp.tapir.docs.openapi

import sttp.tapir.{AnyEndpoint, DocsExtension, Endpoint}
import sttp.tapir.openapi.{Info, OpenAPI}
import sttp.tapir.server.ServerEndpoint

trait OpenAPIDocsInterpreter {

  def openAPIDocsOptions: OpenAPIDocsOptions = OpenAPIDocsOptions.default

  def toOpenAPI[I, E, O, S](e: Endpoint[I, E, O, S], title: String, version: String): OpenAPI =
    toOpenAPI(e, Info(title, version))

  def toOpenAPI[I, E, O, S](e: Endpoint[I, E, O, S], info: Info): OpenAPI =
    EndpointToOpenAPIDocs.toOpenAPI(info, Seq(e), openAPIDocsOptions, List.empty)

  def toOpenAPI[I, E, O, S](e: Endpoint[I, E, O, S], info: Info, docsExtensions: List[DocsExtension[_]]): OpenAPI =
    EndpointToOpenAPIDocs.toOpenAPI(info, Seq(e), openAPIDocsOptions, docsExtensions)

  def toOpenAPI[I, E, O, S, F[_]](se: ServerEndpoint[I, E, O, S, F], title: String, version: String): OpenAPI =
    toOpenAPI(se.endpoint, Info(title, version))

  def toOpenAPI[I, E, O, S, F[_]](se: ServerEndpoint[I, E, O, S, F], info: Info): OpenAPI =
    EndpointToOpenAPIDocs.toOpenAPI(info, Seq(se.endpoint), openAPIDocsOptions, List.empty)

  def toOpenAPI[I, E, O, S, F[_]](se: ServerEndpoint[I, E, O, S, F], info: Info, docsExtensions: List[DocsExtension[_]]): OpenAPI =
    EndpointToOpenAPIDocs.toOpenAPI(info, Seq(se.endpoint), openAPIDocsOptions, docsExtensions)

  def toOpenAPI(es: Iterable[AnyEndpoint], title: String, version: String): OpenAPI =
    toOpenAPI(es, Info(title, version))

  def toOpenAPI(es: Iterable[AnyEndpoint], info: Info): OpenAPI =
    EndpointToOpenAPIDocs.toOpenAPI(info, es, openAPIDocsOptions, List.empty)

  def toOpenAPI(es: Iterable[AnyEndpoint], info: Info, docsExtensions: List[DocsExtension[_]]): OpenAPI =
    EndpointToOpenAPIDocs.toOpenAPI(info, es, openAPIDocsOptions, docsExtensions)

  def serverEndpointsToOpenAPI[F[_]](ses: Iterable[ServerEndpoint[_, _, _, _, F]], title: String, version: String): OpenAPI =
    serverEndpointsToOpenAPI(ses, Info(title, version))

  def serverEndpointsToOpenAPI[F[_]](ses: Iterable[ServerEndpoint[_, _, _, _, F]], info: Info): OpenAPI =
    EndpointToOpenAPIDocs.toOpenAPI(info, ses.map(_.endpoint), openAPIDocsOptions, List.empty)

  def serverEndpointsToOpenAPI[F[_]](
      ses: Iterable[ServerEndpoint[_, _, _, _, F]],
      info: Info,
      docsExtensions: List[DocsExtension[_]]
  ): OpenAPI =
    EndpointToOpenAPIDocs.toOpenAPI(info, ses.map(_.endpoint), openAPIDocsOptions, docsExtensions)
}

object OpenAPIDocsInterpreter {

  def apply(docsOptions: OpenAPIDocsOptions = OpenAPIDocsOptions.default): OpenAPIDocsInterpreter = {
    new OpenAPIDocsInterpreter {
      override def openAPIDocsOptions: OpenAPIDocsOptions = docsOptions
    }
  }
}
