package sttp.tapir.server.vertx.interpreters

import io.vertx.ext.web.{Route, Router, RoutingContext}
import sttp.tapir.server.vertx.encoders.VertxOutputEncoders
import sttp.tapir.server.vertx.handlers.{attachDefaultHandlers, encodeError}
import sttp.tapir.server.vertx.routing.PathMapping.{RouteDefinition, createRoute}
import sttp.tapir.server.vertx.streams.ReadStreamCompatible
import sttp.tapir.server.vertx.VertxEndpointOptions
import sttp.tapir.server.ServerEndpoint
import sttp.capabilities.Streams

import scala.reflect.ClassTag

trait CommonServerInterpreter {

  protected def mountWithDefaultHandlers[F[_], I, E, O, C, S: ReadStreamCompatible](e: ServerEndpoint[I, E, O, C, F])(
      router: Router,
      routeDef: RouteDefinition
  )(implicit endpointOptions: VertxEndpointOptions, ect: Option[ClassTag[E]]): Route =
    attachDefaultHandlers(e.endpoint, createRoute(router, routeDef))

  protected def responseHandlerWithError[F[_], I, E, O, S: ReadStreamCompatible, C <: Streams[S]](
      e: ServerEndpoint[I, E, O, C, F]
  )(implicit
      endpointOptions: VertxEndpointOptions
  ): (Either[E, O], RoutingContext) => Unit = {
    case (Right(result), rc) =>
      VertxOutputEncoders.apply[O, S](e.output, result, isError = false, logRequestHandled(e)).apply(rc)
    case (Left(failure), rc) =>
      encodeError(e.endpoint, rc, failure)
  }

  protected def logRequestHandled[F[_], I, E, O, C](e: ServerEndpoint[I, E, O, C, F])(implicit
      endpointOptions: VertxEndpointOptions
  ): Int => Unit = status => {
    endpointOptions.logRequestHandling.requestHandled(e.endpoint, status)
    ()
  }
}
