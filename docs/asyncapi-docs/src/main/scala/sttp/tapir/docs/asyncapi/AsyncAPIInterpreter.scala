package sttp.tapir.docs.asyncapi

import sttp.tapir.{AnyEndpoint, DocsExtension, Endpoint}
import sttp.tapir.asyncapi.{AsyncAPI, Info, Server}
import sttp.tapir.server.ServerEndpoint

trait AsyncAPIInterpreter {

  def asyncAPIDocsOptions: AsyncAPIDocsOptions = AsyncAPIDocsOptions.default

  def toAsyncAPI[A, I, E, O, R](e: Endpoint[A, I, E, O, R], title: String, version: String): AsyncAPI =
    toAsyncAPI(e, Info(title, version), Nil)
  def toAsyncAPI[A, I, E, O, R](e: Endpoint[A, I, E, O, R], title: String, version: String, servers: Iterable[(String, Server)]): AsyncAPI =
    toAsyncAPI(e, Info(title, version), servers)
  def toAsyncAPI[A, I, E, O, R](e: Endpoint[A, I, E, O, R], info: Info): AsyncAPI =
    toAsyncAPI(e, info, Nil)
  def toAsyncAPI[A, I, E, O, R](e: Endpoint[A, I, E, O, R], info: Info, servers: Iterable[(String, Server)]): AsyncAPI =
    EndpointToAsyncAPIDocs.toAsyncAPI(info, servers, Seq(e), asyncAPIDocsOptions, List.empty)
  def toAsyncAPI[A, I, E, O, R](
      e: Endpoint[A, I, E, O, R],
      info: Info,
      servers: Iterable[(String, Server)],
      docsExtensions: List[DocsExtension[_]]
  ): AsyncAPI = EndpointToAsyncAPIDocs.toAsyncAPI(info, servers, Seq(e), asyncAPIDocsOptions, docsExtensions)

  def toAsyncAPI[R, F[_]](se: ServerEndpoint[R, F], title: String, version: String): AsyncAPI =
    toAsyncAPI(se, Info(title, version), Nil)
  def toAsyncAPI[R, F[_]](
      se: ServerEndpoint[R, F],
      title: String,
      version: String,
      servers: Iterable[(String, Server)]
  ): AsyncAPI =
    toAsyncAPI(se, Info(title, version), servers)
  def toAsyncAPI[E, F[_]](se: ServerEndpoint[E, F], info: Info): AsyncAPI =
    toAsyncAPI(se, info, Nil)
  def toAsyncAPI[R, F[_]](
      se: ServerEndpoint[R, F],
      info: Info,
      servers: Iterable[(String, Server)]
  ): AsyncAPI =
    EndpointToAsyncAPIDocs.toAsyncAPI(info, servers, Seq(se.endpoint), asyncAPIDocsOptions, List.empty)
  def toAsyncAPI[R, F[_]](
      se: ServerEndpoint[R, F],
      info: Info,
      servers: Iterable[(String, Server)],
      docsExtensions: List[DocsExtension[_]]
  ): AsyncAPI = EndpointToAsyncAPIDocs.toAsyncAPI(info, servers, Seq(se.endpoint), asyncAPIDocsOptions, docsExtensions)

  def toAsyncAPI(es: Iterable[AnyEndpoint], title: String, version: String): AsyncAPI =
    toAsyncAPI(es, Info(title, version), Nil)
  def toAsyncAPI(es: Iterable[AnyEndpoint], title: String, version: String, servers: Iterable[(String, Server)]): AsyncAPI =
    toAsyncAPI(es, Info(title, version), servers)
  def toAsyncAPI(es: Iterable[AnyEndpoint], info: Info): AsyncAPI =
    toAsyncAPI(es, info, Nil)
  def toAsyncAPI(es: Iterable[AnyEndpoint], info: Info, servers: Iterable[(String, Server)]): AsyncAPI =
    EndpointToAsyncAPIDocs.toAsyncAPI(info, servers, es, asyncAPIDocsOptions, List.empty)
  def toAsyncAPI(
      es: Iterable[AnyEndpoint],
      info: Info,
      servers: Iterable[(String, Server)],
      docsExtensions: List[DocsExtension[_]]
  ): AsyncAPI = EndpointToAsyncAPIDocs.toAsyncAPI(info, servers, es, asyncAPIDocsOptions, docsExtensions)

  def serverEndpointsToAsyncAPI[F[_]](ses: Iterable[ServerEndpoint[_, F]], title: String, version: String): AsyncAPI =
    serverEndpointsToAsyncAPI(ses, Info(title, version), Nil)
  def serverEndpointsToAsyncAPI[F[_]](
      ses: Iterable[ServerEndpoint[_, F]],
      title: String,
      version: String,
      servers: Iterable[(String, Server)]
  ): AsyncAPI =
    serverEndpointsToAsyncAPI(ses, Info(title, version), servers)
  def serverEndpointsToAsyncAPI[F[_]](ses: Iterable[ServerEndpoint[_, F]], info: Info): AsyncAPI =
    serverEndpointsToAsyncAPI(ses, info, Nil)
  def serverEndpointsToAsyncAPI[F[_]](
      ses: Iterable[ServerEndpoint[_, F]],
      info: Info,
      servers: Iterable[(String, Server)]
  ): AsyncAPI =
    EndpointToAsyncAPIDocs.toAsyncAPI(info, servers, ses.map(_.endpoint), asyncAPIDocsOptions, List.empty)
  def serverEndpointsToAsyncAPI[F[_]](
      ses: Iterable[ServerEndpoint[_, F]],
      info: Info,
      servers: Iterable[(String, Server)],
      docsExtensions: List[DocsExtension[_]]
  ): AsyncAPI = EndpointToAsyncAPIDocs.toAsyncAPI(info, servers, ses.map(_.endpoint), asyncAPIDocsOptions, docsExtensions)
}

object AsyncAPIInterpreter {
  def apply(docsOptions: AsyncAPIDocsOptions = AsyncAPIDocsOptions.default): AsyncAPIInterpreter = {
    new AsyncAPIInterpreter {
      override def asyncAPIDocsOptions: AsyncAPIDocsOptions = docsOptions
    }
  }
}
