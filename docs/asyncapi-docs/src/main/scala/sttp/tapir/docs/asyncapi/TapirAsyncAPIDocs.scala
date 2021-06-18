package sttp.tapir.docs.asyncapi

import sttp.tapir.Endpoint
import sttp.tapir.asyncapi.{AsyncAPI, Info, Server}
import sttp.tapir.server.ServerEndpoint

@deprecated("Use AsyncAPIInterpreter", since = "0.17.1")
trait TapirAsyncAPIDocs {

  def asyncAPIDocsOptions: AsyncAPIDocsOptions = AsyncAPIDocsOptions.default

  implicit class RichOpenAPIEndpoint[I, E, O, S](e: Endpoint[I, E, O, S]) {
    @deprecated("Use AsyncAPIInterpreter.toAsyncAPI", since = "0.17.1")
    def toAsyncAPI(title: String, version: String): AsyncAPI =
      AsyncAPIInterpreter(asyncAPIDocsOptions).toAsyncAPI(e, Info(title, version), Nil)
    @deprecated("Use AsyncAPIInterpreter.toAsyncAPI", since = "0.17.1")
    def toAsyncAPI(title: String, version: String, servers: Iterable[(String, Server)]): AsyncAPI =
      AsyncAPIInterpreter(asyncAPIDocsOptions).toAsyncAPI(e, Info(title, version), servers)
    @deprecated("Use AsyncAPIInterpreter.toAsyncAPI", since = "0.17.1")
    def toAsyncAPI(info: Info): AsyncAPI = AsyncAPIInterpreter(asyncAPIDocsOptions).toAsyncAPI(e, info, Nil)
    @deprecated("Use AsyncAPIInterpreter.toAsyncAPI", since = "0.17.1")
    def toAsyncAPI(info: Info, servers: Iterable[(String, Server)]): AsyncAPI =
      AsyncAPIInterpreter(asyncAPIDocsOptions).toAsyncAPI(e, info, servers)
  }

  implicit class RichOpenAPIEndpoints(es: Iterable[Endpoint[_, _, _, _]]) {
    @deprecated("Use AsyncAPIInterpreter.toAsyncAPI", since = "0.17.1")
    def toAsyncAPI(title: String, version: String): AsyncAPI =
      AsyncAPIInterpreter(asyncAPIDocsOptions).toAsyncAPI(es, Info(title, version), Nil)
    @deprecated("Use AsyncAPIInterpreter.toAsyncAPI", since = "0.17.1")
    def toAsyncAPI(title: String, version: String, servers: Iterable[(String, Server)]): AsyncAPI =
      AsyncAPIInterpreter(asyncAPIDocsOptions).toAsyncAPI(es, Info(title, version), servers)
    @deprecated("Use AsyncAPIInterpreter.toAsyncAPI", since = "0.17.1")
    def toAsyncAPI(info: Info): AsyncAPI = AsyncAPIInterpreter(asyncAPIDocsOptions).toAsyncAPI(es, info, Nil)
    @deprecated("Use AsyncAPIInterpreter.toAsyncAPI", since = "0.17.1")
    def toAsyncAPI(info: Info, servers: Iterable[(String, Server)]): AsyncAPI =
      AsyncAPIInterpreter(asyncAPIDocsOptions).toAsyncAPI(es, info, servers)
  }

  implicit class RichOpenAPIServerEndpoints[F[_]](ses: Iterable[ServerEndpoint[_, _, _, _, F]]) {
    @deprecated("Use AsyncAPIInterpreter.serverEndpointsToAsyncAPI", since = "0.17.1")
    def toAsyncAPI(title: String, version: String): AsyncAPI =
      AsyncAPIInterpreter(asyncAPIDocsOptions).serverEndpointsToAsyncAPI(ses, Info(title, version), Nil)
    @deprecated("Use AsyncAPIInterpreter.serverEndpointsToAsyncAPI", since = "0.17.1")
    def toAsyncAPI(title: String, version: String, servers: Iterable[(String, Server)]): AsyncAPI =
      AsyncAPIInterpreter(asyncAPIDocsOptions).serverEndpointsToAsyncAPI(ses, Info(title, version), servers)
    @deprecated("Use AsyncAPIInterpreter.serverEndpointsToAsyncAPI", since = "0.17.1")
    def toAsyncAPI(info: Info): AsyncAPI =
      AsyncAPIInterpreter(asyncAPIDocsOptions).serverEndpointsToAsyncAPI(ses, info, Nil)
    @deprecated("Use AsyncAPIInterpreter.serverEndpointsToAsyncAPI", since = "0.17.1")
    def toAsyncAPI(info: Info, servers: Iterable[(String, Server)]): AsyncAPI =
      AsyncAPIInterpreter(asyncAPIDocsOptions).serverEndpointsToAsyncAPI(ses, info, servers)
  }
}
