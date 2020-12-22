package sttp.tapir.docs.asyncapi

import sttp.tapir.Endpoint
import sttp.tapir.asyncapi.{AsyncAPI, Info, Server}
import sttp.tapir.server.ServerEndpoint

@deprecated("Use AsyncAPIInterpreter", since = "0.17.1")
trait TapirAsyncAPIDocs {
  implicit class RichOpenAPIEndpoint[I, E, O, S](e: Endpoint[I, E, O, S]) {
    @deprecated("Use AsyncAPIInterpreter.toAsyncAPI", since = "0.17.1")
    def toAsyncAPI(title: String, version: String)(implicit options: AsyncAPIDocsOptions): AsyncAPI =
      AsyncAPIInterpreter.toAsyncAPI(e, Info(title, version), Nil)
    @deprecated("Use AsyncAPIInterpreter.toAsyncAPI", since = "0.17.1")
    def toAsyncAPI(title: String, version: String, servers: Iterable[(String, Server)])(implicit options: AsyncAPIDocsOptions): AsyncAPI =
      AsyncAPIInterpreter.toAsyncAPI(e, Info(title, version), servers)
    @deprecated("Use AsyncAPIInterpreter.toAsyncAPI", since = "0.17.1")
    def toAsyncAPI(info: Info)(implicit options: AsyncAPIDocsOptions): AsyncAPI = AsyncAPIInterpreter.toAsyncAPI(e, info, Nil)
    @deprecated("Use AsyncAPIInterpreter.toAsyncAPI", since = "0.17.1")
    def toAsyncAPI(info: Info, servers: Iterable[(String, Server)])(implicit options: AsyncAPIDocsOptions): AsyncAPI =
      AsyncAPIInterpreter.toAsyncAPI(e, info, servers)
  }

  implicit class RichOpenAPIEndpoints(es: Iterable[Endpoint[_, _, _, _]]) {
    @deprecated("Use AsyncAPIInterpreter.toAsyncAPI", since = "0.17.1")
    def toAsyncAPI(title: String, version: String)(implicit options: AsyncAPIDocsOptions): AsyncAPI =
      AsyncAPIInterpreter.toAsyncAPI(es, Info(title, version), Nil)
    @deprecated("Use AsyncAPIInterpreter.toAsyncAPI", since = "0.17.1")
    def toAsyncAPI(title: String, version: String, servers: Iterable[(String, Server)])(implicit options: AsyncAPIDocsOptions): AsyncAPI =
      AsyncAPIInterpreter.toAsyncAPI(es, Info(title, version), servers)
    @deprecated("Use AsyncAPIInterpreter.toAsyncAPI", since = "0.17.1")
    def toAsyncAPI(info: Info)(implicit options: AsyncAPIDocsOptions): AsyncAPI = AsyncAPIInterpreter.toAsyncAPI(es, info, Nil)
    @deprecated("Use AsyncAPIInterpreter.toAsyncAPI", since = "0.17.1")
    def toAsyncAPI(info: Info, servers: Iterable[(String, Server)])(implicit options: AsyncAPIDocsOptions): AsyncAPI =
      AsyncAPIInterpreter.toAsyncAPI(es, info, servers)
  }

  implicit class RichOpenAPIServerEndpoints[F[_]](ses: Iterable[ServerEndpoint[_, _, _, _, F]]) {
    @deprecated("Use AsyncAPIInterpreter.serverEndpointsToAsyncAPI", since = "0.17.1")
    def toAsyncAPI(title: String, version: String)(implicit options: AsyncAPIDocsOptions): AsyncAPI =
      AsyncAPIInterpreter.serverEndpointsToAsyncAPI(ses, Info(title, version), Nil)
    @deprecated("Use AsyncAPIInterpreter.serverEndpointsToAsyncAPI", since = "0.17.1")
    def toAsyncAPI(title: String, version: String, servers: Iterable[(String, Server)])(implicit options: AsyncAPIDocsOptions): AsyncAPI =
      AsyncAPIInterpreter.serverEndpointsToAsyncAPI(ses, Info(title, version), servers)
    @deprecated("Use AsyncAPIInterpreter.serverEndpointsToAsyncAPI", since = "0.17.1")
    def toAsyncAPI(info: Info)(implicit options: AsyncAPIDocsOptions): AsyncAPI =
      AsyncAPIInterpreter.serverEndpointsToAsyncAPI(ses, info, Nil)
    @deprecated("Use AsyncAPIInterpreter.serverEndpointsToAsyncAPI", since = "0.17.1")
    def toAsyncAPI(info: Info, servers: Iterable[(String, Server)])(implicit options: AsyncAPIDocsOptions): AsyncAPI =
      AsyncAPIInterpreter.serverEndpointsToAsyncAPI(ses, info, servers)
  }
}
