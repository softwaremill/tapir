package sttp.tapir.docs.asyncapi

import sttp.tapir.Endpoint
import sttp.tapir.asyncapi.{AsyncAPI, Info, Server}
import sttp.tapir.server.ServerEndpoint

@deprecated("Use AsyncAPIInterpreter", since = "0.17.1")
trait TapirAsyncAPIDocs {
  implicit class RichOpenAPIEndpoint[I, E, O, S](e: Endpoint[I, E, O, S]) {
    @deprecated("Use AsyncAPIInterpreter.fromEndpoint", since = "0.17.1")
    def toAsyncAPI(title: String, version: String)(implicit options: AsyncAPIDocsOptions): AsyncAPI =
      AsyncAPIInterpreter.fromEndpoint(e, Info(title, version), Nil)
    @deprecated("Use AsyncAPIInterpreter.fromEndpoint", since = "0.17.1")
    def toAsyncAPI(title: String, version: String, servers: Iterable[(String, Server)])(implicit options: AsyncAPIDocsOptions): AsyncAPI =
      AsyncAPIInterpreter.fromEndpoint(e, Info(title, version), servers)
    @deprecated("Use AsyncAPIInterpreter.fromEndpoint", since = "0.17.1")
    def toAsyncAPI(info: Info)(implicit options: AsyncAPIDocsOptions): AsyncAPI = AsyncAPIInterpreter.fromEndpoint(e, info, Nil)
    @deprecated("Use AsyncAPIInterpreter.fromEndpoint", since = "0.17.1")
    def toAsyncAPI(info: Info, servers: Iterable[(String, Server)])(implicit options: AsyncAPIDocsOptions): AsyncAPI =
      AsyncAPIInterpreter.fromEndpoint(e, info, servers)
  }

  implicit class RichOpenAPIEndpoints(es: Iterable[Endpoint[_, _, _, _]]) {
    @deprecated("Use AsyncAPIInterpreter.fromEndpoints", since = "0.17.1")
    def toAsyncAPI(title: String, version: String)(implicit options: AsyncAPIDocsOptions): AsyncAPI =
      AsyncAPIInterpreter.fromEndpoints(es, Info(title, version), Nil)
    @deprecated("Use AsyncAPIInterpreter.fromEndpoints", since = "0.17.1")
    def toAsyncAPI(title: String, version: String, servers: Iterable[(String, Server)])(implicit options: AsyncAPIDocsOptions): AsyncAPI =
      AsyncAPIInterpreter.fromEndpoints(es, Info(title, version), servers)
    @deprecated("Use AsyncAPIInterpreter.fromEndpoints", since = "0.17.1")
    def toAsyncAPI(info: Info)(implicit options: AsyncAPIDocsOptions): AsyncAPI = AsyncAPIInterpreter.fromEndpoints(es, info, Nil)
    @deprecated("Use AsyncAPIInterpreter.fromEndpoints", since = "0.17.1")
    def toAsyncAPI(info: Info, servers: Iterable[(String, Server)])(implicit options: AsyncAPIDocsOptions): AsyncAPI =
      AsyncAPIInterpreter.fromEndpoints(es, info, servers)
  }

  implicit class RichOpenAPIServerEndpoints[F[_]](ses: Iterable[ServerEndpoint[_, _, _, _, F]]) {
    @deprecated("Use AsyncAPIInterpreter.fromServerEndpoints", since = "0.17.1")
    def toAsyncAPI(title: String, version: String)(implicit options: AsyncAPIDocsOptions): AsyncAPI =
      AsyncAPIInterpreter.fromServerEndpoints(ses, Info(title, version), Nil)
    @deprecated("Use AsyncAPIInterpreter.fromServerEndpoints", since = "0.17.1")
    def toAsyncAPI(title: String, version: String, servers: Iterable[(String, Server)])(implicit options: AsyncAPIDocsOptions): AsyncAPI =
      AsyncAPIInterpreter.fromServerEndpoints(ses, Info(title, version), servers)
    @deprecated("Use AsyncAPIInterpreter.fromServerEndpoints", since = "0.17.1")
    def toAsyncAPI(info: Info)(implicit options: AsyncAPIDocsOptions): AsyncAPI = AsyncAPIInterpreter.fromServerEndpoints(ses, info, Nil)
    @deprecated("Use AsyncAPIInterpreter.fromServerEndpoints", since = "0.17.1")
    def toAsyncAPI(info: Info, servers: Iterable[(String, Server)])(implicit options: AsyncAPIDocsOptions): AsyncAPI =
      AsyncAPIInterpreter.fromServerEndpoints(ses, info, servers)
  }
}
