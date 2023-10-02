package sttp.tapir.server.pekkohttp

import org.apache.pekko.http.scaladsl.server.{Directive, Route}
import sttp.capabilities.WebSockets
import sttp.capabilities.pekko.PekkoStreams
import sttp.tapir.Endpoint
import sttp.tapir.server.ServerEndpoint

import scala.concurrent.Future
import scala.reflect.ClassTag

trait PekkoHttpServerInterpreter {
  def toDirective[I, E, O](e: Endpoint[I, E, O, PekkoStreams with WebSockets])(implicit
      serverOptions: PekkoHttpServerOptions
  ): Directive[(I, Future[Either[E, O]] => Route)] =
    new EndpointToPekkoServer(serverOptions).toDirective(e)

  def toRoute[I, E, O](e: Endpoint[I, E, O, PekkoStreams with WebSockets])(logic: I => Future[Either[E, O]])(implicit
      serverOptions: PekkoHttpServerOptions
  ): Route =
    new EndpointToPekkoServer(serverOptions).toRoute(e.serverLogic(logic))

  def toRouteRecoverErrors[I, E, O](
      e: Endpoint[I, E, O, PekkoStreams with WebSockets]
  )(logic: I => Future[O])(implicit eIsThrowable: E <:< Throwable, eClassTag: ClassTag[E], serverOptions: PekkoHttpServerOptions): Route = {
    new EndpointToPekkoServer(serverOptions).toRoute(e.serverLogicRecoverErrors(logic))
  }

  //

  def toDirective[I, E, O](serverEndpoint: ServerEndpoint[I, E, O, PekkoStreams with WebSockets, Future])(implicit
      serverOptions: PekkoHttpServerOptions
  ): Directive[(I, Future[Either[E, O]] => Route)] =
    new EndpointToPekkoServer(serverOptions).toDirective(serverEndpoint.endpoint)

  def toRoute[I, E, O](serverEndpoint: ServerEndpoint[I, E, O, PekkoStreams with WebSockets, Future])(implicit
      serverOptions: PekkoHttpServerOptions
  ): Route = new EndpointToPekkoServer(serverOptions).toRoute(serverEndpoint)

  //

  def toRoute(serverEndpoints: List[ServerEndpoint[_, _, _, PekkoStreams with WebSockets, Future]])(implicit
      serverOptions: PekkoHttpServerOptions
  ): Route = {
    new EndpointToPekkoServer(serverOptions).toRoute(serverEndpoints)
  }
}

object PekkoHttpServerInterpreter extends PekkoHttpServerInterpreter
