package sttp.tapir.server.pekkohttp

import org.apache.pekko.http.scaladsl.server._
import sttp.capabilities.WebSockets
import sttp.capabilities.pekko.PekkoStreams
import sttp.tapir.Endpoint
import sttp.tapir.server.ServerEndpoint

import scala.concurrent.Future
import scala.reflect.ClassTag

@deprecated("Use PekkoHttpServerInterpreter", since = "0.17.1")
trait TapirPekkoHttpServer {
  implicit class RichPekkoHttpEndpoint[I, E, O](e: Endpoint[I, E, O, PekkoStreams with WebSockets])(implicit
      serverOptions: PekkoHttpServerOptions
  ) {
    @deprecated("Use PekkoHttpServerInterpreter.toDirective", since = "0.17.1")
    def toDirective: Directive[(I, Future[Either[E, O]] => Route)] =
      new EndpointToPekkoServer(serverOptions).toDirective(e)

    @deprecated("Use PekkoHttpServerInterpreter.toRoute", since = "0.17.1")
    def toRoute(logic: I => Future[Either[E, O]]): Route =
      new EndpointToPekkoServer(serverOptions).toRoute(e.serverLogic(logic))

    @deprecated("Use PekkoHttpServerInterpreter.toRouteRecoverErrors", since = "0.17.1")
    def toRouteRecoverErrors(
        logic: I => Future[O]
    )(implicit eIsThrowable: E <:< Throwable, eClassTag: ClassTag[E]): Route = {
      new EndpointToPekkoServer(serverOptions).toRoute(e.serverLogicRecoverErrors(logic))
    }
  }

  implicit class RichPekkoHttpServerEndpoint[I, E, O](serverEndpoint: ServerEndpoint[I, E, O, PekkoStreams with WebSockets, Future])(implicit
      serverOptions: PekkoHttpServerOptions
  ) {
    @deprecated("Use PekkoHttpServerInterpreter.toDirective", since = "0.17.1")
    def toDirective: Directive[(I, Future[Either[E, O]] => Route)] =
      new EndpointToPekkoServer(serverOptions).toDirective(serverEndpoint.endpoint)

    @deprecated("Use PekkoHttpServerInterpreter.toRoute", since = "0.17.1")
    def toRoute: Route = new EndpointToPekkoServer(serverOptions).toRoute(serverEndpoint)
  }

  implicit class RichPekkoHttpServerEndpoints(serverEndpoints: List[ServerEndpoint[_, _, _, PekkoStreams with WebSockets, Future]])(implicit
      serverOptions: PekkoHttpServerOptions
  ) {
    @deprecated("Use PekkoHttpServerInterpreter.toRoute", since = "0.17.1")
    def toRoute: Route = {
      new EndpointToPekkoServer(serverOptions).toRoute(serverEndpoints)
    }
  }
}
