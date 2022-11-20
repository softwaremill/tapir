package sttp.tapir.server.vertx.zio

import io.vertx.core.{Future, Handler, Promise}
import io.vertx.ext.web.{Route, Router, RoutingContext}
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.server.interceptor.RequestResult
import sttp.tapir.server.interpreter.{BodyListener, ServerInterpreter}
import sttp.tapir.server.vertx.VertxBodyListener
import sttp.tapir.server.vertx.decoders.{VertxRequestBody, VertxServerRequest}
import sttp.tapir.server.vertx.encoders.{VertxOutputEncoders, VertxToResponseBody}
import sttp.tapir.server.vertx.interpreters.{CommonServerInterpreter, FromVFuture, RunAsync}
import sttp.tapir.server.vertx.routing.PathMapping.extractRouteDefinition
import sttp.tapir.server.vertx.zio.VertxZioServerInterpreter.{RioFromVFuture, ZioRunAsync}
import sttp.tapir.server.vertx.zio.streams._
import sttp.tapir.ztapir.{RIOMonadError, ZServerEndpoint}
import zio._

import java.util.concurrent.atomic.AtomicReference

trait VertxZioServerInterpreter[R] extends CommonServerInterpreter {
  def vertxZioServerOptions: VertxZioServerOptions[RIO[R, *]] = VertxZioServerOptions.default

  def route(e: ZServerEndpoint[R, ZioStreams])(implicit
      runtime: Runtime[R]
  ): Router => Route = { router =>
    mountWithDefaultHandlers(e)(router, extractRouteDefinition(e.endpoint))
      .handler(endpointHandler(e))
  }

  private def endpointHandler(
      e: ZServerEndpoint[R, ZioStreams]
  )(implicit runtime: Runtime[R]): Handler[RoutingContext] = {
    val fromVFuture = new RioFromVFuture[R]
    implicit val monadError: RIOMonadError[R] = new RIOMonadError[R]
    implicit val bodyListener: BodyListener[RIO[R, *], RoutingContext => Future[Void]] =
      new VertxBodyListener[RIO[R, *]](new ZioRunAsync(runtime))
    val zioReadStream = zioReadStreamCompatible(vertxZioServerOptions)
    val interpreter = new ServerInterpreter[ZioStreams, RIO[R, *], RoutingContext => Future[Void], ZioStreams](
      _ => List(e),
      new VertxRequestBody[RIO[R, *], ZioStreams](vertxZioServerOptions, fromVFuture)(zioReadStream),
      new VertxToResponseBody(vertxZioServerOptions)(zioReadStream),
      vertxZioServerOptions.interceptors,
      vertxZioServerOptions.deleteFile
    )

    { rc =>
      val serverRequest = VertxServerRequest(rc)

      def fail(t: Throwable): Unit = {
        if (rc.response().bytesWritten() > 0) rc.response().end()
        rc.fail(t)
      }

      val result: ZIO[R, Throwable, Any] =
        interpreter(serverRequest)
          .flatMap {
            // in vertx, endpoints are attempted to be decoded individually; if this endpoint didn't match - another one might
            case RequestResult.Failure(_) => ZIO.succeed(rc.next())
            case RequestResult.Response(response) =>
              ZIO.async((k: Task[Unit] => Unit) => {
                VertxOutputEncoders(response)
                  .apply(rc)
                  .onComplete(d => {
                    if (d.succeeded()) k(ZIO.unit) else k(ZIO.fail(d.cause()))
                  })
              })
          }
          .catchAll { t => ZIO.attempt(fail(t)) }

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
          val value = runtime.unsafe.fork(result)
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

object VertxZioServerInterpreter {
  def apply[R](serverOptions: VertxZioServerOptions[RIO[R, *]] = VertxZioServerOptions.default[R]): VertxZioServerInterpreter[R] = {
    new VertxZioServerInterpreter[R] {
      override def vertxZioServerOptions: VertxZioServerOptions[RIO[R, *]] = serverOptions
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
