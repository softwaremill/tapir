package sttp.tapir.docs.asyncapi

import sttp.tapir.Endpoint
import sttp.tapir.asyncapi.{AsyncAPI, Info, Server}
import sttp.tapir.server.ServerEndpoint

trait AsyncAPIInterpreter {
  def fromEndpoint[I, E, O, S](e: Endpoint[I, E, O, S], title: String, version: String)(implicit options: AsyncAPIDocsOptions): AsyncAPI =
    fromEndpoint(e, Info(title, version), Nil)
  def fromEndpoint[I, E, O, S](e: Endpoint[I, E, O, S], title: String, version: String, servers: Iterable[(String, Server)])(implicit
      options: AsyncAPIDocsOptions
  ): AsyncAPI =
    fromEndpoint(e, Info(title, version), servers)
  def fromEndpoint[I, E, O, S](e: Endpoint[I, E, O, S], info: Info)(implicit options: AsyncAPIDocsOptions): AsyncAPI =
    fromEndpoint(e, info, Nil)
  def fromEndpoint[I, E, O, S](e: Endpoint[I, E, O, S], info: Info, servers: Iterable[(String, Server)])(implicit
      options: AsyncAPIDocsOptions
  ): AsyncAPI =
    EndpointToAsyncAPIDocs.toAsyncAPI(info, servers, Seq(e), options)

  def fromServerEndpoint[I, E, O, S, F[_]](se: ServerEndpoint[I, E, O, S, F], title: String, version: String)(implicit
      options: AsyncAPIDocsOptions
  ): AsyncAPI =
    fromServerEndpoint(se, Info(title, version), Nil)
  def fromServerEndpoint[I, E, O, S, F[_]](
      se: ServerEndpoint[I, E, O, S, F],
      title: String,
      version: String,
      servers: Iterable[(String, Server)]
  )(implicit
      options: AsyncAPIDocsOptions
  ): AsyncAPI =
    fromServerEndpoint(se, Info(title, version), servers)
  def fromServerEndpoint[I, E, O, S, F[_]](se: ServerEndpoint[I, E, O, S, F], info: Info)(implicit options: AsyncAPIDocsOptions): AsyncAPI =
    fromServerEndpoint(se, info, Nil)
  def fromServerEndpoint[I, E, O, S, F[_]](se: ServerEndpoint[I, E, O, S, F], info: Info, servers: Iterable[(String, Server)])(implicit
      options: AsyncAPIDocsOptions
  ): AsyncAPI =
    EndpointToAsyncAPIDocs.toAsyncAPI(info, servers, Seq(se.endpoint), options)

  def fromEndpoints(es: Iterable[Endpoint[_, _, _, _]], title: String, version: String)(implicit options: AsyncAPIDocsOptions): AsyncAPI =
    fromEndpoints(es, Info(title, version), Nil)
  def fromEndpoints(es: Iterable[Endpoint[_, _, _, _]], title: String, version: String, servers: Iterable[(String, Server)])(implicit
      options: AsyncAPIDocsOptions
  ): AsyncAPI =
    fromEndpoints(es, Info(title, version), servers)
  def fromEndpoints(es: Iterable[Endpoint[_, _, _, _]], info: Info)(implicit options: AsyncAPIDocsOptions): AsyncAPI =
    fromEndpoints(es, info, Nil)
  def fromEndpoints(es: Iterable[Endpoint[_, _, _, _]], info: Info, servers: Iterable[(String, Server)])(implicit
      options: AsyncAPIDocsOptions
  ): AsyncAPI =
    EndpointToAsyncAPIDocs.toAsyncAPI(info, servers, es, options)

  def fromServerEndpoints[F[_]](ses: Iterable[ServerEndpoint[_, _, _, _, F]], title: String, version: String)(implicit
      options: AsyncAPIDocsOptions
  ): AsyncAPI = fromServerEndpoints(ses, Info(title, version), Nil)
  def fromServerEndpoints[F[_]](
      ses: Iterable[ServerEndpoint[_, _, _, _, F]],
      title: String,
      version: String,
      servers: Iterable[(String, Server)]
  )(implicit options: AsyncAPIDocsOptions): AsyncAPI =
    fromServerEndpoints(ses, Info(title, version), servers)
  def fromServerEndpoints[F[_]](ses: Iterable[ServerEndpoint[_, _, _, _, F]], info: Info)(implicit options: AsyncAPIDocsOptions): AsyncAPI =
    fromServerEndpoints(ses, info, Nil)
  def fromServerEndpoints[F[_]](ses: Iterable[ServerEndpoint[_, _, _, _, F]], info: Info, servers: Iterable[(String, Server)])(implicit
      options: AsyncAPIDocsOptions
  ): AsyncAPI =
    EndpointToAsyncAPIDocs.toAsyncAPI(info, servers, ses.map(_.endpoint), options)
}

object AsyncAPIInterpreter extends AsyncAPIInterpreter
