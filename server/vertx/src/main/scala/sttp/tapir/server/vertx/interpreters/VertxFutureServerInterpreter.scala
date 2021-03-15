package sttp.tapir.server.vertx.interpreters

import io.vertx.core.{Handler, Future => VFuture}
import io.vertx.ext.web.{Route, Router, RoutingContext}
import sttp.monad.FutureMonad
import sttp.tapir.Endpoint
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interpreter.ServerInterpreter
import sttp.tapir.server.vertx.VertxFutureEndpointOptions
import sttp.tapir.server.vertx.decoders.{VertxRequestBody, VertxServerRequest}
import sttp.tapir.server.vertx.encoders.{VertxOutputEncoders, VertxToResponseBody}
import sttp.tapir.server.vertx.routing.PathMapping.extractRouteDefinition

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.reflect.ClassTag

trait VertxFutureServerInterpreter extends CommonServerInterpreter {

  /** Given a Router, creates and mounts a Route matching this endpoint, with default error handling
    * @param logic the logic to associate with the endpoint
    * @param endpointOptions options associated to the endpoint, like its logging capabilities, or execution context
    * @return A function, that given a router, will attach this endpoint to it
    */
  def route[I, E, O](e: Endpoint[I, E, O, Any])(logic: I => Future[Either[E, O]])(implicit
      endpointOptions: VertxFutureEndpointOptions
  ): Router => Route =
    route(e.serverLogic(logic))

  /** Given a Router, creates and mounts a Route matching this endpoint, with default error handling
    * The logic will be executed in a blocking context
    * @param logic the logic to associate with the endpoint
    * @param endpointOptions options associated to the endpoint, like its logging capabilities, or execution context
    * @return A function, that given a router, will attach this endpoint to it
    */
  def blockingRoute[I, E, O](e: Endpoint[I, E, O, Any])(logic: I => Future[Either[E, O]])(implicit
      endpointOptions: VertxFutureEndpointOptions
  ): Router => Route =
    blockingRoute(e.serverLogic(logic))

  /** Given a Router, creates and mounts a Route matching this endpoint, with custom error handling
    * @param logic the logic to associate with the endpoint
    * @param endpointOptions options associated to the endpoint, like its logging capabilities, or execution context
    * @return A function, that given a router, will attach this endpoint to it
    */
  def routeRecoverErrors[I, E, O](e: Endpoint[I, E, O, Any])(
      logic: I => Future[O]
  )(implicit endpointOptions: VertxFutureEndpointOptions, eIsThrowable: E <:< Throwable, eClassTag: ClassTag[E]): Router => Route =
    route(e.serverLogicRecoverErrors(logic))

  /** Given a Router, creates and mounts a Route matching this endpoint, with custom error handling
    * The logic will be executed in a blocking context
    * @param logic the logic to associate with the endpoint
    * @param endpointOptions options associated to the endpoint, like its logging capabilities, or execution context
    * @return A function, that given a router, will attach this endpoint to it
    */
  def blockingRouteRecoverErrors[I, E, O](e: Endpoint[I, E, O, Any])(
      logic: I => Future[O]
  )(implicit endpointOptions: VertxFutureEndpointOptions, eIsThrowable: E <:< Throwable, eClassTag: ClassTag[E]): Router => Route =
    blockingRoute(e.serverLogicRecoverErrors(logic))

  /** Given a Router, creates and mounts a Route matching this endpoint, with default error handling
    * @param endpointOptions options associated to the endpoint, like its logging capabilities, or execution context
    * @return A function, that given a router, will attach this endpoint to it
    */
  def route[I, E, O](e: ServerEndpoint[I, E, O, Any, Future])(implicit endpointOptions: VertxFutureEndpointOptions): Router => Route = {
    router =>
      mountWithDefaultHandlers(e)(router, extractRouteDefinition(e.endpoint))
        .handler(endpointHandler(e, endpointOptions))
  }

  /** Given a Router, creates and mounts a Route matching this endpoint, with default error handling
    * The logic will be executed in a blocking context
    * @param endpointOptions options associated to the endpoint, like its logging capabilities, or execution context
    * @return A function, that given a router, will attach this endpoint to it
    */
  def blockingRoute[I, E, O](
      e: ServerEndpoint[I, E, O, Any, Future]
  )(implicit endpointOptions: VertxFutureEndpointOptions): Router => Route = { router =>
    mountWithDefaultHandlers(e)(router, extractRouteDefinition(e.endpoint))
      .blockingHandler(endpointHandler(e, endpointOptions))
  }

  private def endpointHandler[I, E, O, A](
      e: ServerEndpoint[I, E, O, Any, Future],
      serverOptions: VertxFutureEndpointOptions
  ): Handler[RoutingContext] = { rc =>
    implicit val ec: ExecutionContext = serverOptions.executionContextOrCurrentCtx(rc)
    implicit val monad: FutureMonad = new FutureMonad()
    val interpreter = new ServerInterpreter[Any, Future, RoutingContext => Unit, Nothing](
      new VertxServerRequest(rc),
      new VertxRequestBody[Future, Nothing](rc, serverOptions, FutureFromVFuture),
      new VertxToResponseBody[Future, Nothing](serverOptions),
      serverOptions.interceptors
    )

    interpreter(e)
      .flatMap {
        case None           => FutureFromVFuture(rc.response.setStatusCode(404).end())
        case Some(response) => Future.successful(VertxOutputEncoders(response).apply(rc))
      }
      .failed
      .foreach { e =>
        rc.fail(e)
      }
  }

  private[vertx] object FutureFromVFuture extends FromVFuture[Future] {
    def apply[T](f: => VFuture[T]): Future[T] = {
      val promise = Promise[T]()
      f.onComplete { handler =>
        if (handler.succeeded()) {
          promise.success(handler.result())
        } else {
          promise.failure(handler.cause())
        }
      }
      promise.future
    }
  }
}
