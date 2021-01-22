package sttp.tapir.server.vertx

import io.vertx.core.Handler
import io.vertx.scala.ext.web.{Route, Router, RoutingContext}
import sttp.capabilities.Effect
import sttp.monad.{FutureMonad, MonadError}
import sttp.tapir.Endpoint
import sttp.tapir.internal.Params
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.vertx.decoders.VertxInputDecoders.decodeBodyAndInputsThen
import sttp.tapir.server.vertx.encoders.VertxOutputEncoders
import sttp.tapir.server.vertx.handlers.{attachDefaultHandlers, encodeError, tryEncodeError}
import sttp.tapir.server.vertx.routing.PathMapping.{RouteDefinition, createRoute, extractRouteDefinition}

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

trait VertxServerInterpreter {

  /** Given a Router, creates and mounts a Route matching this endpoint, with default error handling
    * @param logic the logic to associate with the endpoint
    * @param endpointOptions options associated to the endpoint, like its logging capabilities, or execution context
    * @return A function, that given a router, will attach this endpoint to it
    */
  def route[I, E, O](e: Endpoint[I, E, O, Effect[Future]])(logic: I => Future[Either[E, O]])(implicit
      endpointOptions: VertxEndpointOptions
  ): Router => Route =
    route(e.serverLogic(logic))

  /** Given a Router, creates and mounts a Route matching this endpoint, with default error handling
    * The logic will be executed in a blocking context
    * @param logic the logic to associate with the endpoint
    * @param endpointOptions options associated to the endpoint, like its logging capabilities, or execution context
    * @return A function, that given a router, will attach this endpoint to it
    */
  def blockingRoute[I, E, O](e: Endpoint[I, E, O, Effect[Future]])(logic: I => Future[Either[E, O]])(implicit
      endpointOptions: VertxEndpointOptions
  ): Router => Route =
    blockingRoute(e.serverLogic(logic))

  /** Given a Router, creates and mounts a Route matching this endpoint, with custom error handling
    * @param logic the logic to associate with the endpoint
    * @param endpointOptions options associated to the endpoint, like its logging capabilities, or execution context
    * @return A function, that given a router, will attach this endpoint to it
    */
  def routeRecoverErrors[I, E, O](e: Endpoint[I, E, O, Effect[Future]])(
      logic: I => Future[O]
  )(implicit endpointOptions: VertxEndpointOptions, eIsThrowable: E <:< Throwable, eClassTag: ClassTag[E]): Router => Route =
    route(e.serverLogicRecoverErrors(logic))

  /** Given a Router, creates and mounts a Route matching this endpoint, with custom error handling
    * The logic will be executed in a blocking context
    * @param logic the logic to associate with the endpoint
    * @param endpointOptions options associated to the endpoint, like its logging capabilities, or execution context
    * @return A function, that given a router, will attach this endpoint to it
    */
  def blockingRouteRecoverErrors[I, E, O](e: Endpoint[I, E, O, Effect[Future]])(
      logic: I => Future[O]
  )(implicit endpointOptions: VertxEndpointOptions, eIsThrowable: E <:< Throwable, eClassTag: ClassTag[E]): Router => Route =
    blockingRoute(e.serverLogicRecoverErrors(logic))

  /** Given a Router, creates and mounts a Route matching this endpoint, with default error handling
    * @param endpointOptions options associated to the endpoint, like its logging capabilities, or execution context
    * @return A function, that given a router, will attach this endpoint to it
    */
  def route[I, E, O](
      e: ServerEndpoint[I, E, O, Effect[Future], Future]
  )(implicit endpointOptions: VertxEndpointOptions): Router => Route = { router =>
    mountWithDefaultHandlers(e)(router, extractRouteDefinition(e.endpoint))(endpointOptions, None)
      .handler(endpointHandler(e)(e.logic, responseHandlerWithError(e))(endpointOptions, None))
  }

  /** Given a Router, creates and mounts a Route matching this endpoint, with default error handling
    * The logic will be executed in a blocking context
    * @param endpointOptions options associated to the endpoint, like its logging capabilities, or execution context
    * @return A function, that given a router, will attach this endpoint to it
    */
  def blockingRoute[I, E, O](
      e: ServerEndpoint[I, E, O, Effect[Future], Future]
  )(implicit endpointOptions: VertxEndpointOptions): Router => Route = { router =>
    mountWithDefaultHandlers(e)(router, extractRouteDefinition(e.endpoint))(endpointOptions, None)
      .blockingHandler(endpointHandler(e)(e.logic, responseHandlerWithError(e))(endpointOptions, None))
  }

  private def mountWithDefaultHandlers[I, E, O](e: ServerEndpoint[I, E, O, Effect[Future], Future])(
      router: Router,
      routeDef: RouteDefinition
  )(implicit endpointOptions: VertxEndpointOptions, ect: Option[ClassTag[E]]): Route =
    attachDefaultHandlers(e.endpoint, createRoute(router, routeDef))

  private def endpointHandler[I, E, O, A](e: ServerEndpoint[I, E, O, Effect[Future], Future])(
      logic: MonadError[Future] => I => Future[A],
      responseHandler: (A, RoutingContext) => Unit
  )(implicit serverOptions: VertxEndpointOptions, ect: Option[ClassTag[E]]): Handler[RoutingContext] = { rc =>
    implicit val monad: MonadError[Future] = new FutureMonad()(serverOptions.executionContextOrCurrentCtx(rc))
    decodeBodyAndInputsThen[E](
      e.endpoint,
      rc,
      { params => logicHandler(e)(logic(monad), responseHandler, rc)(serverOptions, ect)(params) }
    )
  }

  private def logicHandler[I, E, O, T](
      e: ServerEndpoint[I, E, O, Effect[Future], Future]
  )(logic: I => Future[T], responseHandler: (T, RoutingContext) => Unit, rc: RoutingContext)(implicit
      serverOptions: VertxEndpointOptions,
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
  }

  private def responseHandlerWithError[I, E, O](
      e: ServerEndpoint[I, E, O, Effect[Future], Future]
  )(implicit endpointOptions: VertxEndpointOptions): (Either[E, O], RoutingContext) => Unit = { (output, rc) =>
    output match {
      case Left(failure) =>
        encodeError(e.endpoint, rc, failure)
      case Right(result) =>
        responseHandlerNoError(e)(endpointOptions)(result, rc)
    }
  }

  private def responseHandlerNoError[I, E, O](
      e: ServerEndpoint[I, E, O, Effect[Future], Future]
  )(implicit endpointOptions: VertxEndpointOptions): (O, RoutingContext) => Unit = { (result, rc) =>
    VertxOutputEncoders.apply[O](e.output, result, isError = false, logRequestHandled(e))(endpointOptions)(rc)
  }

  private def logRequestHandled[I, E, O](e: ServerEndpoint[I, E, O, Effect[Future], Future])(implicit
      endpointOptions: VertxEndpointOptions
  ): Int => Unit =
    status => endpointOptions.logRequestHandling.requestHandled(e.endpoint, status): Unit

}

object VertxServerInterpreter extends VertxServerInterpreter
