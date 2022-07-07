package sttp.tapir.server.vertx

import sttp.tapir.server.{ServerEndpoint, ServerRoutes}

import scala.concurrent.Future

object VertxFutureServerRoutes {

  implicit class VertxServerEndpointOps(se: ServerEndpoint[Any, Future]) {

    def route(interpreter: VertxFutureServerInterpreter): VertxFutureServerRoutes =
      ServerRoutes.one(se)(interpreter.route)

    def blockingRoute(interpreter: VertxFutureServerInterpreter): VertxFutureServerRoutes =
      ServerRoutes.one(se)(interpreter.blockingRoute)
  }
}
