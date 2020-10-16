package sttp.tapir.docs.asyncapi

import sttp.tapir.Endpoint
import sttp.tapir.asyncapi.{AsyncAPI, Info, Server}
import sttp.tapir.server.ServerEndpoint

trait TapirAsyncAPIDocs {
  implicit class RichOpenAPIEndpoint[I, E, O, S](e: Endpoint[I, E, O, S]) {
    def toAsyncAPI(title: String, version: String)(implicit options: AsyncAPIDocsOptions): AsyncAPI = toAsyncAPI(Info(title, version), Nil)
    def toAsyncAPI(title: String, version: String, servers: Iterable[(String, Server)])(implicit options: AsyncAPIDocsOptions): AsyncAPI =
      toAsyncAPI(Info(title, version), servers)
    def toAsyncAPI(info: Info)(implicit options: AsyncAPIDocsOptions): AsyncAPI = toAsyncAPI(info, Nil)
    def toAsyncAPI(info: Info, servers: Iterable[(String, Server)])(implicit options: AsyncAPIDocsOptions): AsyncAPI =
      EndpointToAsyncAPIDocs.toAsyncAPI(info, servers, Seq(e), options)
  }

  implicit class RichOpenAPIEndpoints(es: Iterable[Endpoint[_, _, _, _]]) {
    def toAsyncAPI(title: String, version: String)(implicit options: AsyncAPIDocsOptions): AsyncAPI = toAsyncAPI(Info(title, version), Nil)
    def toAsyncAPI(title: String, version: String, servers: Iterable[(String, Server)])(implicit options: AsyncAPIDocsOptions): AsyncAPI =
      toAsyncAPI(Info(title, version), servers)
    def toAsyncAPI(info: Info)(implicit options: AsyncAPIDocsOptions): AsyncAPI = toAsyncAPI(info, Nil)
    def toAsyncAPI(info: Info, servers: Iterable[(String, Server)])(implicit options: AsyncAPIDocsOptions): AsyncAPI =
      EndpointToAsyncAPIDocs.toAsyncAPI(info, servers, es, options)
  }

  implicit class RichOpenAPIServerEndpoints[F[_]](serverEndpoints: Iterable[ServerEndpoint[_, _, _, _, F]]) {
    def toAsyncAPI(title: String, version: String)(implicit options: AsyncAPIDocsOptions): AsyncAPI = toAsyncAPI(Info(title, version), Nil)
    def toAsyncAPI(title: String, version: String, servers: Iterable[(String, Server)])(implicit options: AsyncAPIDocsOptions): AsyncAPI =
      toAsyncAPI(Info(title, version), servers)
    def toAsyncAPI(info: Info)(implicit options: AsyncAPIDocsOptions): AsyncAPI = toAsyncAPI(info, Nil)
    def toAsyncAPI(info: Info, servers: Iterable[(String, Server)])(implicit options: AsyncAPIDocsOptions): AsyncAPI =
      EndpointToAsyncAPIDocs.toAsyncAPI(info, servers, serverEndpoints.map(_.endpoint), options)
  }
}
