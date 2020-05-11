package sttp.tapir.server

import io.vertx.core.Handler
import io.vertx.scala.ext.web.{Route, Router, RoutingContext}
import sttp.tapir._
import sttp.tapir.internal.Params
import sttp.tapir.monad.{FutureMonadError, MonadError}
import sttp.tapir.server.vertx.decoders.VertxInputDecoders._
import sttp.tapir.server.vertx.encoders.VertxOutputEncoders
import sttp.tapir.server.vertx.handlers._
import sttp.tapir.server.vertx.routing.PathMapping._

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

package object vertx {

  implicit class VertxEndpoint[I, E, O, D](e: Endpoint[I, E, O, D]) {

    /**
      * Given a Router, creates and mounts a Route matching this endpoint, with default error handling
      * @param logic the logic to associate with the endpoint
      * @param endpointOptions options associated to the endpoint, like its logging capabilities, or execution context
      * @return A function, that given a router, will attach this endpoint to it
      */
    def route(logic: I => Future[Either[E, O]])(implicit endpointOptions: VertxEndpointOptions): Router => Route =
      e.serverLogic(logic).route

    /**
      * Given a Router, creates and mounts a Route matching this endpoint, with default error handling
      * The logic will be executed in a blocking context
      * @param logic the logic to associate with the endpoint
      * @param endpointOptions options associated to the endpoint, like its logging capabilities, or execution context
      * @return A function, that given a router, will attach this endpoint to it
      */
    def blockingRoute(logic: I => Future[Either[E, O]])(implicit endpointOptions: VertxEndpointOptions): Router => Route =
      e.serverLogic(logic).blockingRoute

    /**
      * Given a Router, creates and mounts a Route matching this endpoint, with custom error handling
      * @param logic the logic to associate with the endpoint
      * @param endpointOptions options associated to the endpoint, like its logging capabilities, or execution context
      * @return A function, that given a router, will attach this endpoint to it
      */
    def routeRecoverErrors(
        logic: I => Future[O]
    )(implicit endpointOptions: VertxEndpointOptions, eIsThrowable: E <:< Throwable, eClassTag: ClassTag[E]): Router => Route =
      e.serverLogicRecoverErrors(logic).route

    /**
      * Given a Router, creates and mounts a Route matching this endpoint, with custom error handling
      * The logic will be executed in a blocking context
      * @param logic the logic to associate with the endpoint
      * @param endpointOptions options associated to the endpoint, like its logging capabilities, or execution context
      * @return A function, that given a router, will attach this endpoint to it
      */
    def blockingRouteRecoverErrors(
        logic: I => Future[O]
    )(implicit endpointOptions: VertxEndpointOptions, eIsThrowable: E <:< Throwable, eClassTag: ClassTag[E]): Router => Route =
      e.serverLogicRecoverErrors(logic).blockingRoute
  }

  implicit class VertxServerEndpoint[I, E, O, D](e: ServerEndpoint[I, E, O, D, Future]) {

    /**
      * Given a Router, creates and mounts a Route matching this endpoint, with default error handling
      * @param endpointOptions options associated to the endpoint, like its logging capabilities, or execution context
      * @return A function, that given a router, will attach this endpoint to it
      */
    def route(implicit endpointOptions: VertxEndpointOptions): Router => Route = { router =>
      mountWithDefaultHandlers(router, extractRouteDefinition(e.endpoint))(endpointOptions, None)
        .handler(endpointHandler(e.logic, responseHandlerWithError)(endpointOptions, None))
    }

    /**
      * Given a Router, creates and mounts a Route matching this endpoint, with default error handling
      * The logic will be executed in a blocking context
      * @param endpointOptions options associated to the endpoint, like its logging capabilities, or execution context
      * @return A function, that given a router, will attach this endpoint to it
      */
    def blockingRoute(implicit endpointOptions: VertxEndpointOptions): Router => Route = { router =>
      mountWithDefaultHandlers(router, extractRouteDefinition(e.endpoint))(endpointOptions, None)
        .blockingHandler(endpointHandler(e.logic, responseHandlerWithError)(endpointOptions, None))
    }

    private def mountWithDefaultHandlers(
        router: Router,
        routeDef: RouteDefinition
    )(implicit endpointOptions: VertxEndpointOptions, ect: Option[ClassTag[E]]): Route =
      attachDefaultHandlers(e.endpoint, createRoute(router, routeDef))

    private def endpointHandler[A](
        logic: MonadError[Future] => I => Future[A],
        responseHandler: (A, RoutingContext) => Unit
    )(implicit serverOptions: VertxEndpointOptions, ect: Option[ClassTag[E]]): Handler[RoutingContext] = { rc =>
      val monad = new FutureMonadError()(serverOptions.executionContextOrCurrentCtx(rc))
      decodeBodyAndInputsThen[E](
        e.endpoint,
        rc,
        { params => logicHandler(logic(monad), responseHandler, rc)(serverOptions, ect)(params) }
      )
    }

    private def logicHandler[T](logic: I => Future[T], responseHandler: (T, RoutingContext) => Unit, rc: RoutingContext)(implicit
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
        .recover {
          case cause =>
            tryEncodeError(e.endpoint, rc, cause)
            serverOptions.logRequestHandling.logicException(e.endpoint, cause)(serverOptions.logger): Unit
        }
    }

    private def responseHandlerWithError(implicit endpointOptions: VertxEndpointOptions): (Either[E, O], RoutingContext) => Unit = {
      (output, rc) =>
        output match {
          case Left(failure) =>
            encodeError(e.endpoint, rc, failure)
          case Right(result) =>
            responseHandlerNoError(endpointOptions)(result, rc)
        }
    }

    private def responseHandlerNoError(implicit endpointOptions: VertxEndpointOptions): (O, RoutingContext) => Unit = { (result, rc) =>
      VertxOutputEncoders.apply[O](e.output, result, isError = false, logRequestHandled)(endpointOptions)(rc)
    }

    private def logRequestHandled(implicit endpointOptions: VertxEndpointOptions): Int => Unit =
      status => endpointOptions.logRequestHandling.requestHandled(e.endpoint, status): Unit
  }
}
