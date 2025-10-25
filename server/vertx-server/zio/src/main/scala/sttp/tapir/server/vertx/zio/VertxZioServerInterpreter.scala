package sttp.tapir.server.vertx.zio

import io.vertx.core.{Future, Handler, Promise}
import io.vertx.ext.web.{Route, Router, RoutingContext}
import sttp.capabilities.WebSockets
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.server.interceptor.RequestResult
import sttp.tapir.server.interpreter.{BodyListener, ServerInterpreter}
import sttp.tapir.server.vertx.VertxBodyListener
import sttp.tapir.server.vertx.VertxErrorHandler
import sttp.tapir.server.vertx.decoders.{VertxRequestBody, VertxServerRequest}
import sttp.tapir.server.vertx.encoders.{VertxOutputEncoders, VertxToResponseBody}
import sttp.tapir.server.vertx.interpreters.{CommonServerInterpreter, FromVFuture, RunAsync}
import sttp.tapir.server.vertx.routing.PathMapping.extractRouteDefinition
import sttp.tapir.server.vertx.zio.VertxZioServerInterpreter.{RioFromVFuture, VertxFutureToRIO, ZioRunAsync}
import sttp.tapir.server.vertx.zio.streams._
import sttp.tapir.ztapir._
import zio._

import java.util.concurrent.atomic.AtomicReference

trait VertxZioServerInterpreter[R] extends CommonServerInterpreter with VertxErrorHandler {
  def vertxZioServerOptions[R2 <: R]: VertxZioServerOptions[R2] = VertxZioServerOptions.default[R].widen

  def route[R2](e: ZServerEndpoint[R2, ZioStreams with WebSockets])(implicit
      runtime: Runtime[R & R2]
  ): Router => Route = { router =>
    val routeDef = extractRouteDefinition(e.endpoint)
    optionsRouteIfCORSDefined(e.widen)(router, routeDef, vertxZioServerOptions)
      .foreach(_.handler(endpointHandler(e)))
    mountWithDefaultHandlers(e.widen)(router, routeDef, vertxZioServerOptions)
      .handler(endpointHandler(e))
  }

  private def endpointHandler[R2](
      e: ZServerEndpoint[R2, ZioStreams with WebSockets]
  )(implicit runtime: Runtime[R & R2]): Handler[RoutingContext] = {
    val fromVFuture = new RioFromVFuture[R & R2]
    implicit val monadError: RIOMonadError[R & R2] = new RIOMonadError[R & R2]
    implicit val bodyListener: BodyListener[RIO[R & R2, *], RoutingContext => Future[Void]] =
      new VertxBodyListener[RIO[R & R2, *]](new ZioRunAsync(runtime))
    val zioReadStream = zioReadStreamCompatible(vertxZioServerOptions[R & R2])
    val interpreter = new ServerInterpreter[ZioStreams with WebSockets, RIO[R & R2, *], RoutingContext => Future[Void], ZioStreams](
      _ => List(e.widen[R & R2]),
      new VertxRequestBody[RIO[R & R2, *], ZioStreams](vertxZioServerOptions[R & R2], fromVFuture)(zioReadStream),
      new VertxToResponseBody(vertxZioServerOptions)(zioReadStream),
      vertxZioServerOptions.interceptors,
      vertxZioServerOptions.deleteFile
    )
    new Handler[RoutingContext] {
      override def handle(rc: RoutingContext) = {
        val serverRequest = VertxServerRequest(rc)

        val result: ZIO[R & R2, Throwable, Any] =
          interpreter(serverRequest)
            .flatMap {
              // in vertx, endpoints are attempted to be decoded individually; if this endpoint didn't match - another one might
              case RequestResult.Failure(_)         => ZIO.succeed(rc.next())
              case RequestResult.Response(response) =>
                ZIO.async((k: Task[Unit] => Unit) => {
                  VertxOutputEncoders(response)
                    .apply(rc)
                    .onComplete(d => {
                      if (d.succeeded()) k(ZIO.unit) else k(ZIO.fail(d.cause()))
                    })
                })
            }
            .catchAll { t => handleError(rc, t).asRIO }

        // we obtain the cancel token only after the effect is run, so we need to pass it to the exception handler
        // via a mutable ref; however, before this is done, it's possible an exception has already been reported;
        // if so, we need to use this fact to cancel the operation nonetheless
        val cancelRef = new AtomicReference[Option[Either[Throwable, FiberId => Exit[Throwable, Any]]]](None)

        rc.response.exceptionHandler { (t: Throwable) =>
          cancelRef.getAndSet(Some(Left(t))).collect { case Right(c) =>
            rc.vertx()
              .executeBlocking[Unit](
                (promise: Promise[Unit]) => {
                  c(FiberId.None)
                  promise.complete(())
                },
                false
              )
          }
          ()
        }

        val canceler: FiberId => Exit[Throwable, Any] = {
          Unsafe.unsafe(implicit u => {
            val value = runtime.unsafe.fork(ZIO.yieldNow *> result)
            (fiberId: FiberId) => runtime.unsafe.run(value.interruptAs(fiberId)).flattenExit
          })
        }

        cancelRef.getAndSet(Some(Right(canceler))).collect { case Left(_) =>
          rc.vertx()
            .executeBlocking[Unit](
              (promise: Promise[Unit]) => {
                canceler(FiberId.None)
                promise.complete(())
              },
              false
            )
        }

        ()
      }
    }
  }
}

object VertxZioServerInterpreter {
  def apply[R](serverOptions: VertxZioServerOptions[R] = VertxZioServerOptions.default[R]): VertxZioServerInterpreter[R] = {
    new VertxZioServerInterpreter[R] {
      override def vertxZioServerOptions[R2 <: R]: VertxZioServerOptions[R2] = serverOptions.widen[R2]
    }
  }

  private[vertx] class RioFromVFuture[R] extends FromVFuture[RIO[R, *]] {
    def apply[T](f: => Future[T]): RIO[R, T] = f.asRIO
  }

  private[vertx] class ZioRunAsync[R](runtime: Runtime[R]) extends RunAsync[RIO[R, *]] {
    override def apply[T](f: => RIO[R, T]): Unit = Unsafe.unsafe(implicit u => {
      runtime.unsafe.runToFuture(f)
    })
  }

  implicit class VertxFutureToRIO[A](f: => Future[A]) {
    def asRIO[R]: RIO[R, A] = {
      ZIO.async { cb =>
        f.onComplete { handler =>
          if (handler.succeeded()) {
            cb(ZIO.succeed(handler.result()))
          } else {
            cb(ZIO.fail(handler.cause()))
          }
        }
      }
    }
  }
}
