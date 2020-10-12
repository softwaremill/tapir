package sttp.tapir.server.akkahttp

import akka.http.scaladsl.server._
import sttp.capabilities.WebSockets
import sttp.capabilities.akka.AkkaStreams
import sttp.tapir.Endpoint
import sttp.tapir.server.ServerEndpoint

import scala.concurrent.Future
import scala.reflect.ClassTag

trait TapirAkkaHttpServer {
  implicit class RichAkkaHttpEndpoint[I, E, O](e: Endpoint[I, E, O, AkkaStreams with WebSockets])(implicit
      serverOptions: AkkaHttpServerOptions
  ) {
    def toDirective: Directive[(I, Future[Either[E, O]] => Route)] =
      new EndpointToAkkaServer(serverOptions).toDirective(e)

    def toRoute(logic: I => Future[Either[E, O]]): Route =
      new EndpointToAkkaServer(serverOptions).toRoute(e.serverLogic(logic))

    def toRouteRecoverErrors(
        logic: I => Future[O]
    )(implicit eIsThrowable: E <:< Throwable, eClassTag: ClassTag[E]): Route = {
      new EndpointToAkkaServer(serverOptions).toRoute(e.serverLogicRecoverErrors(logic))
    }
  }

  implicit class RichAkkaHttpServerEndpoint[I, E, O](serverEndpoint: ServerEndpoint[I, E, O, AkkaStreams with WebSockets, Future])(implicit
      serverOptions: AkkaHttpServerOptions
  ) {
    def toDirective: Directive[(I, Future[Either[E, O]] => Route)] =
      new EndpointToAkkaServer(serverOptions).toDirective(serverEndpoint.endpoint)

    def toRoute: Route = new EndpointToAkkaServer(serverOptions).toRoute(serverEndpoint)
  }

  implicit class RichAkkaHttpServerEndpoints(serverEndpoints: List[ServerEndpoint[_, _, _, AkkaStreams with WebSockets, Future]])(implicit
      serverOptions: AkkaHttpServerOptions
  ) {
    def toRoute: Route = {
      new EndpointToAkkaServer(serverOptions).toRoute(serverEndpoints)
    }
  }
}
