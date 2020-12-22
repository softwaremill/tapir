package sttp.tapir.docs.asyncapi

import sttp.tapir.Endpoint
import sttp.tapir.asyncapi.{AsyncAPI, Info, Server}
import sttp.tapir.server.ServerEndpoint

trait AsyncAPIInterpreter {
  def toAsyncAPI[I, E, O, S](e: Endpoint[I, E, O, S], title: String, version: String)(implicit options: AsyncAPIDocsOptions): AsyncAPI =
    toAsyncAPI(e, Info(title, version), Nil)
  def toAsyncAPI[I, E, O, S](e: Endpoint[I, E, O, S], title: String, version: String, servers: Iterable[(String, Server)])(implicit
      options: AsyncAPIDocsOptions
  ): AsyncAPI =
    toAsyncAPI(e, Info(title, version), servers)
  def toAsyncAPI[I, E, O, S](e: Endpoint[I, E, O, S], info: Info)(implicit options: AsyncAPIDocsOptions): AsyncAPI =
    toAsyncAPI(e, info, Nil)
  def toAsyncAPI[I, E, O, S](e: Endpoint[I, E, O, S], info: Info, servers: Iterable[(String, Server)])(implicit
      options: AsyncAPIDocsOptions
  ): AsyncAPI =
    EndpointToAsyncAPIDocs.toAsyncAPI(info, servers, Seq(e), options)

  def toAsyncAPI[I, E, O, S, F[_]](se: ServerEndpoint[I, E, O, S, F], title: String, version: String)(implicit
      options: AsyncAPIDocsOptions
  ): AsyncAPI =
    toAsyncAPI(se, Info(title, version), Nil)
  def toAsyncAPI[I, E, O, S, F[_]](
      se: ServerEndpoint[I, E, O, S, F],
      title: String,
      version: String,
      servers: Iterable[(String, Server)]
  )(implicit
      options: AsyncAPIDocsOptions
  ): AsyncAPI =
    toAsyncAPI(se, Info(title, version), servers)
  def toAsyncAPI[I, E, O, S, F[_]](se: ServerEndpoint[I, E, O, S, F], info: Info)(implicit options: AsyncAPIDocsOptions): AsyncAPI =
    toAsyncAPI(se, info, Nil)
  def toAsyncAPI[I, E, O, S, F[_]](se: ServerEndpoint[I, E, O, S, F], info: Info, servers: Iterable[(String, Server)])(implicit
      options: AsyncAPIDocsOptions
  ): AsyncAPI =
    EndpointToAsyncAPIDocs.toAsyncAPI(info, servers, Seq(se.endpoint), options)

  def toAsyncAPI(es: Iterable[Endpoint[_, _, _, _]], title: String, version: String)(implicit options: AsyncAPIDocsOptions): AsyncAPI =
    toAsyncAPI(es, Info(title, version), Nil)
  def toAsyncAPI(es: Iterable[Endpoint[_, _, _, _]], title: String, version: String, servers: Iterable[(String, Server)])(implicit
      options: AsyncAPIDocsOptions
  ): AsyncAPI =
    toAsyncAPI(es, Info(title, version), servers)
  def toAsyncAPI(es: Iterable[Endpoint[_, _, _, _]], info: Info)(implicit options: AsyncAPIDocsOptions): AsyncAPI =
    toAsyncAPI(es, info, Nil)
  def toAsyncAPI(es: Iterable[Endpoint[_, _, _, _]], info: Info, servers: Iterable[(String, Server)])(implicit
      options: AsyncAPIDocsOptions
  ): AsyncAPI =
    EndpointToAsyncAPIDocs.toAsyncAPI(info, servers, es, options)

  def serverEndpointsToAsyncAPI[F[_]](ses: Iterable[ServerEndpoint[_, _, _, _, F]], title: String, version: String)(implicit
      options: AsyncAPIDocsOptions
  ): AsyncAPI = serverEndpointsToAsyncAPI(ses, Info(title, version), Nil)
  def serverEndpointsToAsyncAPI[F[_]](
      ses: Iterable[ServerEndpoint[_, _, _, _, F]],
      title: String,
      version: String,
      servers: Iterable[(String, Server)]
  )(implicit options: AsyncAPIDocsOptions): AsyncAPI =
    serverEndpointsToAsyncAPI(ses, Info(title, version), servers)
  def serverEndpointsToAsyncAPI[F[_]](ses: Iterable[ServerEndpoint[_, _, _, _, F]], info: Info)(implicit
      options: AsyncAPIDocsOptions
  ): AsyncAPI =
    serverEndpointsToAsyncAPI(ses, info, Nil)
  def serverEndpointsToAsyncAPI[F[_]](ses: Iterable[ServerEndpoint[_, _, _, _, F]], info: Info, servers: Iterable[(String, Server)])(
      implicit options: AsyncAPIDocsOptions
  ): AsyncAPI =
    EndpointToAsyncAPIDocs.toAsyncAPI(info, servers, ses.map(_.endpoint), options)
}

object AsyncAPIInterpreter extends AsyncAPIInterpreter
