package sttp.tapir.docs.openapi

import sttp.apispec.openapi.{Info, OpenAPI}
import sttp.tapir.docs.apispec.DocsExtension
import sttp.tapir.{AnyEndpoint, Endpoint}
import sttp.tapir.server.ServerEndpoint

trait OpenAPIDocsInterpreter {

  def openAPIDocsOptions: OpenAPIDocsOptions = OpenAPIDocsOptions.default

  def toOpenAPI[A, I, E, O, R](e: Endpoint[A, I, E, O, R], title: String, version: String): OpenAPI =
    toOpenAPI(e, Info(title, version))

  def toOpenAPI[A, I, E, O, R](e: Endpoint[A, I, E, O, R], info: Info): OpenAPI =
    EndpointToOpenAPIDocs.toOpenAPI(info, Seq(e), openAPIDocsOptions, List.empty)

  def toOpenAPI[A, I, E, O, R](e: Endpoint[A, I, E, O, R], info: Info, docsExtensions: List[DocsExtension[_]]): OpenAPI =
    EndpointToOpenAPIDocs.toOpenAPI(info, Seq(e), openAPIDocsOptions, docsExtensions)

  def toOpenAPI[R, F[_]](se: ServerEndpoint[R, F], title: String, version: String): OpenAPI =
    toOpenAPI(se.endpoint, Info(title, version))

  def toOpenAPI[R, F[_]](se: ServerEndpoint[R, F], info: Info): OpenAPI =
    EndpointToOpenAPIDocs.toOpenAPI(info, Seq(se.endpoint), openAPIDocsOptions, List.empty)

  def toOpenAPI[R, F[_]](
      se: ServerEndpoint[R, F],
      info: Info,
      docsExtensions: List[DocsExtension[_]]
  ): OpenAPI =
    EndpointToOpenAPIDocs.toOpenAPI(info, Seq(se.endpoint), openAPIDocsOptions, docsExtensions)

  def toOpenAPI(es: Iterable[AnyEndpoint], title: String, version: String): OpenAPI =
    toOpenAPI(es, Info(title, version))

  def toOpenAPI(es: Iterable[AnyEndpoint], info: Info): OpenAPI =
    EndpointToOpenAPIDocs.toOpenAPI(info, es, openAPIDocsOptions, List.empty)

  def toOpenAPI(es: Iterable[AnyEndpoint], info: Info, docsExtensions: List[DocsExtension[_]]): OpenAPI =
    EndpointToOpenAPIDocs.toOpenAPI(info, es, openAPIDocsOptions, docsExtensions)

  def serverEndpointsToOpenAPI[F[_]](ses: Iterable[ServerEndpoint[_, F]], title: String, version: String): OpenAPI =
    serverEndpointsToOpenAPI(ses, Info(title, version))

  def serverEndpointsToOpenAPI[F[_]](ses: Iterable[ServerEndpoint[_, F]], info: Info): OpenAPI =
    EndpointToOpenAPIDocs.toOpenAPI(info, ses.map(_.endpoint), openAPIDocsOptions, List.empty)

  def serverEndpointsToOpenAPI[F[_]](
      ses: Iterable[ServerEndpoint[_, F]],
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
