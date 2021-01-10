package sttp.tapir.server.vertx.interpreters

import io.vertx.core.{Future => VFuture, Handler}
import io.vertx.ext.web.{Route, Router, RoutingContext}
import sttp.monad.{FutureMonad, MonadError}
import sttp.tapir.server.vertx.handlers.tryEncodeError
import sttp.tapir.server.vertx.decoders.VertxInputDecoders.decodeBodyAndInputsThen
import sttp.tapir.server.vertx.routing.PathMapping.extractRouteDefinition
import sttp.tapir.server.vertx.VertxFutureEndpointOptions
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.internal.Params
import sttp.tapir.Endpoint

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

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
  def route[I, E, O](e: ServerEndpoint[I, E, O, Any, Future])(implicit endpointOptions: VertxFutureEndpointOptions): Router => Route = { router =>
    implicit val ect: Option[ClassTag[E]] = None
    mountWithDefaultHandlers(e)(router, extractRouteDefinition(e.endpoint))
      .handler(endpointHandler(e)(e.logic, responseHandlerWithError(e))(endpointOptions, None))
  }

  /** Given a Router, creates and mounts a Route matching this endpoint, with default error handling
    * The logic will be executed in a blocking context
    * @param endpointOptions options associated to the endpoint, like its logging capabilities, or execution context
    * @return A function, that given a router, will attach this endpoint to it
    */
  def blockingRoute[I, E, O](e: ServerEndpoint[I, E, O, Any, Future])(implicit endpointOptions: VertxFutureEndpointOptions): Router => Route = {
    router =>
      implicit val ect: Option[ClassTag[E]] = None
      mountWithDefaultHandlers(e)(router, extractRouteDefinition(e.endpoint))
        .blockingHandler(endpointHandler(e)(e.logic, responseHandlerWithError(e))(endpointOptions, None))
  }

  private def endpointHandler[I, E, O, A](e: ServerEndpoint[I, E, O, Any, Future])(
      logic: MonadError[Future] => I => Future[A],
      responseHandler: (A, RoutingContext) => Unit
  )(implicit serverOptions: VertxFutureEndpointOptions, ect: Option[ClassTag[E]]): Handler[RoutingContext] = { rc =>
    val monad = new FutureMonad()(serverOptions.executionContextOrCurrentCtx(rc))
    decodeBodyAndInputsThen(
      e.endpoint,
      rc,
      logicHandler(e)(logic(monad), responseHandler, rc)
    )
  }

  private def logicHandler[I, E, O, T](
      e: ServerEndpoint[I, E, O, Any, Future]
  )(logic: I => Future[T], responseHandler: (T, RoutingContext) => Unit, rc: RoutingContext)(implicit
      serverOptions: VertxFutureEndpointOptions,
      ect: Option[ClassTag[E]]
  ): Params => Unit = { params =>
    implicit val ec: ExecutionContext = serverOptions.executionContextOrCurrentCtx(rc)
    Try(logic(params.asAny.asInstanceOf[I]))
      .map { result =>
        result.onComplete {
          case Success(result) =>
            responseHandler(result, rc)
          case Failure(cause) =>
            serverOptions.logRequestHandling.logicException(e.endpoint, cause)(serverOptions.logger)
            tryEncodeError(e.endpoint, rc, cause)
        }
      }
      .recover { case cause =>
        tryEncodeError(e.endpoint, rc, cause)
        serverOptions.logRequestHandling.logicException(e.endpoint, cause)(serverOptions.logger): Unit
      }
    ()
  }

  implicit class VertxFutureForScala[A](future: => VFuture[A]) {

    def asScala: Future[A] = {
      val promise = Promise[A]()
      future.onComplete { handler =>
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
