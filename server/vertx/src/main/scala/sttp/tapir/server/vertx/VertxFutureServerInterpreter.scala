package sttp.tapir.server.vertx

import io.vertx.core.logging.LoggerFactory
import io.vertx.core.{Handler, Future => VFuture}
import io.vertx.ext.web.{Route, Router, RoutingContext}
import sttp.monad.FutureMonad
import sttp.tapir.internal.NoStreams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.RequestResult
import sttp.tapir.server.interpreter.{BodyListener, ServerInterpreter}
import sttp.tapir.server.vertx.VertxFutureServerInterpreter.FutureFromVFuture
import sttp.tapir.server.vertx.decoders.{VertxRequestBody, VertxServerRequest}
import sttp.tapir.server.vertx.encoders.{VertxOutputEncoders, VertxToResponseBody}
import sttp.tapir.server.vertx.interpreters.{CommonServerInterpreter, FromVFuture}
import sttp.tapir.server.vertx.routing.PathMapping.extractRouteDefinition
import sttp.tapir.server.vertx.streams.ReadStreamCompatible

import scala.concurrent.{ExecutionContext, Future, Promise}

trait VertxFutureServerInterpreter extends CommonServerInterpreter {

  private val logger = LoggerFactory.getLogger(VertxFutureServerInterpreter.getClass)

  def vertxFutureServerOptions: VertxFutureServerOptions = VertxFutureServerOptions.default

  /** Given a Router, creates and mounts a Route matching this endpoint, with default error handling
    *
    * @return
    *   A function, that given a router, will attach this endpoint to it
    */
  def route[A, U, I, E, O](e: ServerEndpoint[A, U, I, E, O, Any, Future]): Router => Route = { router =>
    mountWithDefaultHandlers(e)(router, extractRouteDefinition(e.endpoint))
      .handler(endpointHandler(e))
  }

  /** Given a Router, creates and mounts a Route matching this endpoint, with default error handling The logic will be executed in a
    * blocking context
    *
    * @return
    *   A function, that given a router, will attach this endpoint to it
    */
  def blockingRoute[A, U, I, E, O](
      e: ServerEndpoint[A, U, I, E, O, Any, Future]
  ): Router => Route = { router =>
    mountWithDefaultHandlers(e)(router, extractRouteDefinition(e.endpoint))
      .blockingHandler(endpointHandler(e))
  }

  private def endpointHandler[A, U, I, E, O](
      e: ServerEndpoint[A, U, I, E, O, Any, Future]
  ): Handler[RoutingContext] = { rc =>
    implicit val ec: ExecutionContext = vertxFutureServerOptions.executionContextOrCurrentCtx(rc)
    implicit val monad: FutureMonad = new FutureMonad()
    implicit val bodyListener: BodyListener[Future, RoutingContext => VFuture[Void]] = new VertxBodyListener[Future]
    val interpreter = new ServerInterpreter[Any, Future, RoutingContext => VFuture[Void], NoStreams](
      new VertxRequestBody(rc, vertxFutureServerOptions, FutureFromVFuture)(ReadStreamCompatible.incompatible),
      new VertxToResponseBody(vertxFutureServerOptions)(ReadStreamCompatible.incompatible),
      vertxFutureServerOptions.interceptors,
      vertxFutureServerOptions.deleteFile
    )
    val serverRequest = new VertxServerRequest(rc)

    interpreter(serverRequest, e)
      .flatMap {
        case RequestResult.Failure(_)         => FutureFromVFuture(rc.response.setStatusCode(404).end())
        case RequestResult.Response(response) => FutureFromVFuture(VertxOutputEncoders(response).apply(rc))
      }
      .failed
      .foreach { ex =>
        logger.error("Error while processing the request", ex)
        if (rc.response().bytesWritten() > 0) rc.response().end()
        rc.fail(ex)
      }
  }
}

object VertxFutureServerInterpreter {
  def apply(serverOptions: VertxFutureServerOptions = VertxFutureServerOptions.default): VertxFutureServerInterpreter = {
    new VertxFutureServerInterpreter {
      override def vertxFutureServerOptions: VertxFutureServerOptions = serverOptions
    }
  }

  private[vertx] object FutureFromVFuture extends FromVFuture[Future] {
    def apply[T](f: => VFuture[T]): Future[T] = f.asScala
  }

  implicit class VertxFutureToScalaFuture[A](future: => VFuture[A]) {
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
