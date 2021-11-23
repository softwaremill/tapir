package sttp.tapir.docs.openapi

import sttp.tapir.{AnyEndpoint, DocsExtension, Endpoint}
import sttp.tapir.openapi.{Info, OpenAPI}
import sttp.tapir.server.ServerEndpoint

trait OpenAPIDocsInterpreter {

  def openAPIDocsOptions: OpenAPIDocsOptions = OpenAPIDocsOptions.default

  def toOpenAPI[A, I, E, O, R](e: Endpoint[A, I, E, O, R], title: String, version: String): OpenAPI =
    toOpenAPI(e, Info(title, version))

  def toOpenAPI[A, I, E, O, R](e: Endpoint[A, I, E, O, R], info: Info): OpenAPI =
    EndpointToOpenAPIDocs.toOpenAPI(info, Seq(EndpointWithDocsMetadata(e)), openAPIDocsOptions, List.empty)

  def toOpenAPI[A, I, E, O, R](e: Endpoint[A, I, E, O, R], info: Info, docsExtensions: List[DocsExtension[_]]): OpenAPI =
    EndpointToOpenAPIDocs.toOpenAPI(info, Seq(EndpointWithDocsMetadata(e)), openAPIDocsOptions, docsExtensions)

  def toOpenAPI[R, F[_]](se: ServerEndpoint[R, F], title: String, version: String): OpenAPI =
    toOpenAPI(se.endpoint, Info(title, version))

  def toOpenAPI[R, F[_]](se: ServerEndpoint[R, F], info: Info): OpenAPI =
    EndpointToOpenAPIDocs.toOpenAPI(info, Seq(EndpointWithDocsMetadata(se.endpoint)), openAPIDocsOptions, List.empty)

  def toOpenAPI[R, F[_]](
      se: ServerEndpoint[R, F],
      info: Info,
      docsExtensions: List[DocsExtension[_]]
  ): OpenAPI =
    EndpointToOpenAPIDocs.toOpenAPI(info, Seq(EndpointWithDocsMetadata(se.endpoint)), openAPIDocsOptions, docsExtensions)

  def toOpenAPI(e: EndpointWithDocsMetadata, title: String, version: String): OpenAPI =
    toOpenAPI(e, Info(title, version))

  def toOpenAPI(e: EndpointWithDocsMetadata, info: Info): OpenAPI =
    EndpointToOpenAPIDocs.toOpenAPI(info, Seq(e), openAPIDocsOptions, List.empty)

  def toOpenAPI(
      e: EndpointWithDocsMetadata,
      info: Info,
      docsExtensions: List[DocsExtension[_]]
  ): OpenAPI =
    EndpointToOpenAPIDocs.toOpenAPI(info, Seq(e), openAPIDocsOptions, docsExtensions)

  def toOpenAPI(es: Iterable[AnyEndpoint], title: String, version: String): OpenAPI =
    toOpenAPI(es, Info(title, version))

  def toOpenAPI(es: Iterable[AnyEndpoint], info: Info): OpenAPI =
    EndpointToOpenAPIDocs.toOpenAPI(info, es.map(EndpointWithDocsMetadata(_)), openAPIDocsOptions, List.empty)

  def toOpenAPI(es: Iterable[AnyEndpoint], info: Info, docsExtensions: List[DocsExtension[_]]): OpenAPI =
    EndpointToOpenAPIDocs.toOpenAPI(info, es.map(EndpointWithDocsMetadata(_)), openAPIDocsOptions, docsExtensions)

  def serverEndpointsToOpenAPI[F[_]](ses: Iterable[ServerEndpoint[_, F]], title: String, version: String): OpenAPI =
    serverEndpointsToOpenAPI(ses, Info(title, version))

  def serverEndpointsToOpenAPI[F[_]](ses: Iterable[ServerEndpoint[_, F]], info: Info): OpenAPI =
    EndpointToOpenAPIDocs.toOpenAPI(info, ses.map(e => EndpointWithDocsMetadata(e.endpoint)), openAPIDocsOptions, List.empty)

  def serverEndpointsToOpenAPI[F[_]](
      ses: Iterable[ServerEndpoint[_, F]],
      info: Info,
      docsExtensions: List[DocsExtension[_]]
  ): OpenAPI =
    EndpointToOpenAPIDocs.toOpenAPI(info, ses.map(e => EndpointWithDocsMetadata(e.endpoint)), openAPIDocsOptions, docsExtensions)

  def endpointsWithDocsMetadataToOpenAPI(es: Iterable[EndpointWithDocsMetadata], title: String, version: String): OpenAPI =
    endpointsWithDocsMetadataToOpenAPI(es, Info(title, version))

  def endpointsWithDocsMetadataToOpenAPI(es: Iterable[EndpointWithDocsMetadata], info: Info): OpenAPI =
    EndpointToOpenAPIDocs.toOpenAPI(info, es, openAPIDocsOptions, List.empty)

  def endpointsWithDocsMetadataToOpenAPI(
      es: Iterable[EndpointWithDocsMetadata],
      info: Info,
      docsExtensions: List[DocsExtension[_]]
  ): OpenAPI =
    EndpointToOpenAPIDocs.toOpenAPI(info, es, openAPIDocsOptions, docsExtensions)
}

object OpenAPIDocsInterpreter {

  def apply(docsOptions: OpenAPIDocsOptions = OpenAPIDocsOptions.default): OpenAPIDocsInterpreter = {
    new OpenAPIDocsInterpreter {
      override def openAPIDocsOptions: OpenAPIDocsOptions = docsOptions
    }
  }
}
