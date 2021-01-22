package sttp.tapir.server

import io.vertx.scala.ext.web.{Route, Router}
import sttp.capabilities.Effect
import sttp.tapir.Endpoint

import scala.concurrent.Future
import scala.reflect.ClassTag

package object vertx {

  implicit class VertxEndpoint[I, E, O](e: Endpoint[I, E, O, Effect[Future]]) {

    /** Given a Router, creates and mounts a Route matching this endpoint, with default error handling
      * @param logic the logic to associate with the endpoint
      * @param endpointOptions options associated to the endpoint, like its logging capabilities, or execution context
      * @return A function, that given a router, will attach this endpoint to it
      */
    @deprecated("Use VertxServerInterpreter.route", since = "0.17.1")
    def route(logic: I => Future[Either[E, O]])(implicit endpointOptions: VertxEndpointOptions): Router => Route =
      VertxServerInterpreter.route(e)(logic)

    /** Given a Router, creates and mounts a Route matching this endpoint, with default error handling
      * The logic will be executed in a blocking context
      * @param logic the logic to associate with the endpoint
      * @param endpointOptions options associated to the endpoint, like its logging capabilities, or execution context
      * @return A function, that given a router, will attach this endpoint to it
      */
    @deprecated("Use VertxServerInterpreter.blockingRoute", since = "0.17.1")
    def blockingRoute(logic: I => Future[Either[E, O]])(implicit endpointOptions: VertxEndpointOptions): Router => Route =
      VertxServerInterpreter.blockingRoute(e)(logic)

    /** Given a Router, creates and mounts a Route matching this endpoint, with custom error handling
      * @param logic the logic to associate with the endpoint
      * @param endpointOptions options associated to the endpoint, like its logging capabilities, or execution context
      * @return A function, that given a router, will attach this endpoint to it
      */
    @deprecated("Use VertxServerInterpreter.routeRecoverErrors", since = "0.17.1")
    def routeRecoverErrors(
        logic: I => Future[O]
    )(implicit endpointOptions: VertxEndpointOptions, eIsThrowable: E <:< Throwable, eClassTag: ClassTag[E]): Router => Route =
      VertxServerInterpreter.routeRecoverErrors(e)(logic)

    /** Given a Router, creates and mounts a Route matching this endpoint, with custom error handling
      * The logic will be executed in a blocking context
      * @param logic the logic to associate with the endpoint
      * @param endpointOptions options associated to the endpoint, like its logging capabilities, or execution context
      * @return A function, that given a router, will attach this endpoint to it
      */
    @deprecated("Use VertxServerInterpreter.blockingRouteRecoverErrors", since = "0.17.1")
    def blockingRouteRecoverErrors(
        logic: I => Future[O]
    )(implicit endpointOptions: VertxEndpointOptions, eIsThrowable: E <:< Throwable, eClassTag: ClassTag[E]): Router => Route =
      VertxServerInterpreter.blockingRouteRecoverErrors(e)(logic)
  }

  implicit class VertxServerEndpoint[I, E, O](e: ServerEndpoint[I, E, O, Effect[Future], Future]) {

    /** Given a Router, creates and mounts a Route matching this endpoint, with default error handling
      * @param endpointOptions options associated to the endpoint, like its logging capabilities, or execution context
      * @return A function, that given a router, will attach this endpoint to it
      */
    @deprecated("Use VertxServerInterpreter.route", since = "0.17.1")
    def route(implicit endpointOptions: VertxEndpointOptions): Router => Route = VertxServerInterpreter.route(e)

    /** Given a Router, creates and mounts a Route matching this endpoint, with default error handling
      * The logic will be executed in a blocking context
      * @param endpointOptions options associated to the endpoint, like its logging capabilities, or execution context
      * @return A function, that given a router, will attach this endpoint to it
      */
    @deprecated("Use VertxServerInterpreter.blockingRoute", since = "0.17.1")
    def blockingRoute(implicit endpointOptions: VertxEndpointOptions): Router => Route = VertxServerInterpreter.route(e)
  }
}
