package sttp.tapir.server.vertx

import io.vertx.core.logging.LoggerFactory
import io.vertx.core.{Handler, Future => VFuture}
import io.vertx.ext.web.{Route, Router, RoutingContext}
import sttp.monad.FutureMonad
import sttp.capabilities.WebSockets
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.RequestResult
import sttp.tapir.server.interpreter.{BodyListener, ServerInterpreter}
import sttp.tapir.server.vertx.VertxFutureServerInterpreter.{FutureFromVFuture, FutureRunAsync}
import sttp.tapir.server.vertx.decoders.{VertxRequestBody, VertxServerRequest}
import sttp.tapir.server.vertx.encoders.{VertxOutputEncoders, VertxToResponseBody}
import sttp.tapir.server.vertx.interpreters.{CommonServerInterpreter, FromVFuture, RunAsync}
import sttp.tapir.server.vertx.routing.PathMapping.extractRouteDefinition
import sttp.tapir.server.vertx.streams.{ReadStreamCompatible, VertxStreams}

import scala.concurrent.{ExecutionContext, Future, Promise}

trait VertxFutureServerInterpreter extends CommonServerInterpreter {

  private val logger = LoggerFactory.getLogger(VertxFutureServerInterpreter.getClass)

  def vertxFutureServerOptions: VertxFutureServerOptions = VertxFutureServerOptions.default

  /** Given a Router, creates and mounts a Route matching this endpoint, with default error handling
    *
    * @return
    *   A function, that given a router, will attach this endpoint to it
    */
  def route[A, U, I, E, O](e: ServerEndpoint[VertxStreams with WebSockets, Future]): Router => Route = { router =>
    mountWithDefaultHandlers(e)(router, extractRouteDefinition(e.endpoint))
      .handler(endpointHandler(e))
  }

  /** Given a Router, creates and mounts a Route matching this endpoint, with default error handling The logic will be executed in a
    * blocking context
    *
    * @return
    *   A function, that given a router, will attach this endpoint to it
    */
  def blockingRoute(e: ServerEndpoint[VertxStreams with WebSockets, Future]): Router => Route = { router =>
    mountWithDefaultHandlers(e)(router, extractRouteDefinition(e.endpoint))
      .blockingHandler(endpointHandler(e))
  }

  private def endpointHandler(
      e: ServerEndpoint[VertxStreams with WebSockets, Future]
  ): Handler[RoutingContext] = { rc =>
    implicit val ec: ExecutionContext = vertxFutureServerOptions.executionContextOrCurrentCtx(rc)
    implicit val monad: FutureMonad = new FutureMonad()
    implicit val bodyListener: BodyListener[Future, RoutingContext => VFuture[Void]] = new VertxBodyListener[Future](FutureRunAsync)
    val reactiveStreamsReadStream: ReadStreamCompatible[VertxStreams] = streams.reactiveStreamsReadStreamCompatible()
    val interpreter = new ServerInterpreter[VertxStreams with WebSockets, Future, RoutingContext => VFuture[Void], VertxStreams](
      _ => List(e),
      new VertxRequestBody(vertxFutureServerOptions, FutureFromVFuture)(reactiveStreamsReadStream),
      new VertxToResponseBody(vertxFutureServerOptions)(reactiveStreamsReadStream),
      vertxFutureServerOptions.interceptors,
      vertxFutureServerOptions.deleteFile
    )

    val serverRequest = VertxServerRequest(rc)

    interpreter(serverRequest)
      .flatMap {
        // in vertx, endpoints are attempted to be decoded individually; if this endpoint didn't match - another one might
        case RequestResult.Failure(_)         => Future.successful(rc.next())
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

  private[vertx] object FutureRunAsync extends RunAsync[Future] {
    override def apply[T](f: => Future[T]): Unit = f
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
