package sttp.tapir.server

import io.vertx.core.Handler
import io.vertx.lang.scala.VertxExecutionContext
import io.vertx.scala.core.Vertx
import io.vertx.scala.ext.web.{Route, Router, RoutingContext}
import sttp.tapir._
import sttp.tapir.internal.Params
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
    def route(logic: I => Future[Either[E, O]])
             (implicit endpointOptions: VertxEndpointOptions)
             : Router => Route = { router =>
      mountWithDefaultHandlers(router, extractRouteDefinition(e))(endpointOptions, None)
        .handler(endpointHandler(logic, responseHandlerWithError)(endpointOptions, None))
    }

    /**
     * Given a Router, creates and mounts a Route matching this endpoint, with default error handling
     * The logic will be executed in a blocking context
     * @param logic the logic to associate with the endpoint
     * @param endpointOptions options associated to the endpoint, like its logging capabilities, or execution context
     * @return A function, that given a router, will attach this endpoint to it
     */
    def blockingRoute(logic: I => Future[Either[E, O]])
                     (implicit endpointOptions: VertxEndpointOptions)
                     : Router => Route = { router =>
      mountWithDefaultHandlers(router, extractRouteDefinition(e))(endpointOptions, None)
        .blockingHandler(endpointHandler(logic, responseHandlerWithError)(endpointOptions, None))
    }

    /**
     * Given a Router, creates and mounts a Route matching this endpoint, with custom error handling
     * @param logic the logic to associate with the endpoint
     * @param endpointOptions options associated to the endpoint, like its logging capabilities, or execution context
     * @return A function, that given a router, will attach this endpoint to it
     */
    def routeRecoverErrors(logic: I => Future[O])
                          (implicit endpointOptions: VertxEndpointOptions, eIsThrowable: E <:< Throwable, eClassTag: ClassTag[E])
                          : Router => Route = { router =>
      val ect = Some(implicitly[ClassTag[E]])
      mountWithDefaultHandlers(router, extractRouteDefinition(e))(endpointOptions, ect)
        .handler(endpointHandler(logic, responseHandlerNoError)(endpointOptions, ect))
    }

    /**
     * Given a Router, creates and mounts a Route matching this endpoint, with custom error handling
     * The logic will be executed in a blocking context
     * @param logic the logic to associate with the endpoint
     * @param endpointOptions options associated to the endpoint, like its logging capabilities, or execution context
     * @return A function, that given a router, will attach this endpoint to it
     */
    def blockingRouteRecoverErrors(logic: I => Future[O])
                                  (implicit endpointOptions: VertxEndpointOptions, eIsThrowable: E <:< Throwable, eClassTag: ClassTag[E])
                                  : Router => Route = { router =>
      val ect = Some(implicitly[ClassTag[E]])
      mountWithDefaultHandlers(router, extractRouteDefinition(e))(endpointOptions, ect)
        .blockingHandler(endpointHandler(logic, responseHandlerNoError)(endpointOptions, ect))
    }

    private def mountWithDefaultHandlers(router: Router, routeDef: RouteDefinition)
                                        (implicit endpointOptions: VertxEndpointOptions, ect: Option[ClassTag[E]]): Route =
      attachDefaultHandlers(e, createRoute(router, routeDef))

    private def endpointHandler[A](logic: I => Future[A], responseHandler: (A, RoutingContext) => Unit)
                                  (implicit serverOptions: VertxEndpointOptions, ect: Option[ClassTag[E]]): Handler[RoutingContext] = { rc =>
      decodeBodyAndInputsThen[E](e, rc, { params =>
        logicHandler(logic, responseHandler, rc)(serverOptions, ect)(params)
      })
    }

    private def logicHandler[T](logic: I => Future[T], responseHandler: (T, RoutingContext) => Unit, rc: RoutingContext)
                               (implicit serverOptions: VertxEndpointOptions, ect: Option[ClassTag[E]]): Params => Unit = { params =>
      implicit val ec: ExecutionContext = serverOptions.executionContextOr(rc.executionContext)
      Try(logic(params.asAny.asInstanceOf[I]))
        .map { result =>
          result.onComplete {
            case Success(result) =>
              responseHandler(result, rc)
            case Failure(cause) =>
              serverOptions.logRequestHandling.logicException(e, cause)(serverOptions.logger)
              tryEncodeError(e, rc, cause)
          }
        }
        .recover { case cause =>
          tryEncodeError(e, rc, cause)
          serverOptions.logRequestHandling.logicException(e, cause)(serverOptions.logger)
        }
      }

    private def responseHandlerWithError(implicit endpointOptions: VertxEndpointOptions): (Either[E, O], RoutingContext) => Unit = { (output, rc) =>
      output match {
        case Left(failure) =>
          encodeError(e, rc, failure)
        case Right(result) =>
          responseHandlerNoError(endpointOptions)(result, rc)
      }
    }

    private def responseHandlerNoError(implicit endpointOptions: VertxEndpointOptions): (O, RoutingContext) => Unit = { (result, rc) =>
      VertxOutputEncoders.apply[O](e.output, result, isError = false, logRequestHandled)(endpointOptions)(rc)
    }

    private def logRequestHandled(implicit endpointOptions: VertxEndpointOptions): Int => Unit = status =>
      endpointOptions.logRequestHandling.requestHandled(e, status)

  }

  private[vertx] implicit class RichContextHandler(rc: RoutingContext) {
    implicit lazy val executionContext: ExecutionContext = VertxExecutionContext(rc.vertx.getOrCreateContext)
  }

}
