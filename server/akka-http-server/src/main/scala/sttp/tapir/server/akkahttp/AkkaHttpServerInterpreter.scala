package sttp.tapir.server.akkahttp

import akka.http.scaladsl.server.{Directive, Route}
import sttp.capabilities.WebSockets
import sttp.capabilities.akka.AkkaStreams
import sttp.tapir.Endpoint
import sttp.tapir.server.ServerEndpoint

import scala.concurrent.Future
import scala.reflect.ClassTag

trait AkkaHttpServerInterpreter {
  def toDirective[I, E, O](e: Endpoint[I, E, O, AkkaStreams with WebSockets])(implicit
      serverOptions: AkkaHttpServerOptions
  ): Directive[(I, Future[Either[E, O]] => Route)] =
    new EndpointToAkkaServer(serverOptions).toDirective(e)

  def toRoute[I, E, O](e: Endpoint[I, E, O, AkkaStreams with WebSockets])(logic: I => Future[Either[E, O]])(implicit
      serverOptions: AkkaHttpServerOptions
  ): Route =
    new EndpointToAkkaServer(serverOptions).toRoute(e.serverLogic(logic))

  def toRouteRecoverErrors[I, E, O](
      e: Endpoint[I, E, O, AkkaStreams with WebSockets]
  )(logic: I => Future[O])(implicit eIsThrowable: E <:< Throwable, eClassTag: ClassTag[E], serverOptions: AkkaHttpServerOptions): Route = {
    new EndpointToAkkaServer(serverOptions).toRoute(e.serverLogicRecoverErrors(logic))
  }

  //

  def toDirective[I, E, O](serverEndpoint: ServerEndpoint[I, E, O, AkkaStreams with WebSockets, Future])(implicit
      serverOptions: AkkaHttpServerOptions
  ): Directive[(I, Future[Either[E, O]] => Route)] =
    new EndpointToAkkaServer(serverOptions).toDirective(serverEndpoint.endpoint)

  def toRoute[I, E, O](serverEndpoint: ServerEndpoint[I, E, O, AkkaStreams with WebSockets, Future])(implicit
      serverOptions: AkkaHttpServerOptions
  ): Route = new EndpointToAkkaServer(serverOptions).toRoute(serverEndpoint)

  //

  def toRoute(serverEndpoints: List[ServerEndpoint[_, _, _, AkkaStreams with WebSockets, Future]])(implicit
      serverOptions: AkkaHttpServerOptions
  ): Route = {
    new EndpointToAkkaServer(serverOptions).toRoute(serverEndpoints)
  }
}

object AkkaHttpServerInterpreter extends AkkaHttpServerInterpreter
