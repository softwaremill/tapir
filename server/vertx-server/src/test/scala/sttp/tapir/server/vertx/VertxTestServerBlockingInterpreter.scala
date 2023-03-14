package sttp.tapir.server.vertx

import io.vertx.core.Vertx
import io.vertx.ext.web.{Route, Router}
import sttp.capabilities.WebSockets
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.vertx.streams.VertxStreams

import scala.concurrent.Future

class VertxTestServerBlockingInterpreter(vertx: Vertx) extends VertxTestServerInterpreter(vertx) {
  override def route(es: List[ServerEndpoint[VertxStreams with WebSockets, Future]], interceptors: Interceptors): Router => Route = { router =>
    val options: VertxFutureServerOptions = interceptors(VertxFutureServerOptions.customiseInterceptors).options
    val interpreter = VertxFutureServerInterpreter(options)
    es.map(interpreter.blockingRoute(_)(router)).last
  }
}
