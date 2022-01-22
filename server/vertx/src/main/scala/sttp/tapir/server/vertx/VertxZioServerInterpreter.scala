package sttp.tapir.server.vertx

import io.vertx.core.logging.LoggerFactory
import io.vertx.core.{Future, Handler}
import io.vertx.ext.web.{Route, Router, RoutingContext}
import sttp.capabilities.zio.ZioStreams
import sttp.monad.MonadError
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.RequestResult
import sttp.tapir.server.interpreter.{BodyListener, ServerInterpreter}
import sttp.tapir.server.vertx.VertxZioServerInterpreter.{RioFromVFuture, monadError}
import sttp.tapir.server.vertx.decoders.{VertxRequestBody, VertxServerRequest}
import sttp.tapir.server.vertx.encoders.{VertxOutputEncoders, VertxToResponseBody}
import sttp.tapir.server.vertx.interpreters.{CommonServerInterpreter, FromVFuture}
import sttp.tapir.server.vertx.routing.PathMapping.extractRouteDefinition
import sttp.tapir.server.vertx.streams.zio._
import zio._

import java.util.concurrent.atomic.AtomicReference

trait VertxZioServerInterpreter[R] extends CommonServerInterpreter {

  private val logger = LoggerFactory.getLogger(VertxZioServerInterpreter.getClass)

  def vertxZioServerOptions: VertxZioServerOptions[RIO[R, *]] = VertxZioServerOptions.default

  def route(e: ServerEndpoint[ZioStreams, RIO[R, *]])(implicit
      runtime: Runtime[R]
  ): Router => Route = { router =>
    mountWithDefaultHandlers(e)(router, extractRouteDefinition(e.endpoint))
      .handler(endpointHandler(e))
  }

  private def endpointHandler(
      e: ServerEndpoint[ZioStreams, RIO[R, *]]
  )(implicit runtime: Runtime[R]): Handler[RoutingContext] = {
    val fromVFuture = new RioFromVFuture[R]
    implicit val bodyListener: BodyListener[RIO[R, *], RoutingContext => Future[Void]] = new VertxBodyListener[RIO[R, *]]
    val zioReadStream = zioReadStreamCompatible(vertxZioServerOptions)
    val interpreter = new ServerInterpreter[ZioStreams, RIO[R, *], RoutingContext => Future[Void], ZioStreams](
      List(e),
      new VertxToResponseBody(vertxZioServerOptions)(zioReadStream),
      vertxZioServerOptions.interceptors,
      vertxZioServerOptions.deleteFile
    )

    { rc =>
      val serverRequest = new VertxServerRequest(rc)

      val result: ZIO[R, Throwable, Any] =
        interpreter(serverRequest, new VertxRequestBody[RIO[R, *], ZioStreams](rc, vertxZioServerOptions, fromVFuture)(zioReadStream))
          .flatMap {
            case RequestResult.Failure(decodeFailureContexts) => fromVFuture(rc.response.setStatusCode(404).end())
            case RequestResult.Response(response) =>
              Task.effectAsync((k: Task[Unit] => Unit) => {
                VertxOutputEncoders(response)
                  .apply(rc)
                  .onComplete(d => {
                    if (d.succeeded()) k(Task.unit) else k(Task.fail(d.cause()))
                  })
              })
          }
          .catchAll { ex =>
            RIO.effect({
              logger.error("Error while processing the request", ex)
              if (rc.response().bytesWritten() > 0) rc.response().end()
              rc.fail(ex)
            })
          }

      // we obtain the cancel token only after the effect is run, so we need to pass it to the exception handler
      // via a mutable ref; however, before this is done, it's possible an exception has already been reported;
      // if so, we need to use this fact to cancel the operation nonetheless
      val cancelRef = new AtomicReference[Option[Either[Throwable, Fiber.Id => Exit[Throwable, Any]]]](None)

      rc.response.exceptionHandler { (t: Throwable) =>
        cancelRef.getAndSet(Some(Left(t))).collect { case Right(c) =>
          c(Fiber.Id.None)
        }
        ()
      }

      val canceler = runtime.unsafeRunAsyncCancelable(result) { _ => () }
      cancelRef.getAndSet(Some(Right(canceler))).collect { case Left(_) =>
        canceler(Fiber.Id.None)
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

  private[vertx] implicit def monadError[R]: MonadError[RIO[R, *]] = new MonadError[RIO[R, *]] {
    override def unit[T](t: T): RIO[R, T] = Task.succeed(t)
    override def map[T, T2](fa: RIO[R, T])(f: T => T2): RIO[R, T2] = fa.map(f)
    override def flatMap[T, T2](fa: RIO[R, T])(f: T => RIO[R, T2]): RIO[R, T2] = fa.flatMap(f)
    override def error[T](t: Throwable): RIO[R, T] = Task.fail(t)
    override protected def handleWrappedError[T](rt: RIO[R, T])(h: PartialFunction[Throwable, RIO[R, T]]): RIO[R, T] = rt.catchSome(h)
    override def eval[T](t: => T): RIO[R, T] = Task.effect(t)
    override def suspend[T](t: => RIO[R, T]): RIO[R, T] = RIO.effectSuspend(t)
    override def flatten[T](ffa: RIO[R, RIO[R, T]]): RIO[R, T] = ffa.flatten
    override def ensure[T](f: RIO[R, T], e: => RIO[R, Unit]): RIO[R, T] = f.ensuring(e.catchAll(_ => Task.unit))
  }

  private[vertx] class RioFromVFuture[R] extends FromVFuture[RIO[R, *]] {
    def apply[T](f: => Future[T]): RIO[R, T] = f.asRIO
  }

  implicit class VertxFutureToRIO[A](f: => Future[A]) {
    def asRIO[R]: RIO[R, A] = {
      RIO.effectAsync { cb =>
        f.onComplete { handler =>
          if (handler.succeeded()) {
            cb(Task.succeed(handler.result()))
          } else {
            cb(Task.fail(handler.cause()))
          }
        }
      }
    }
  }
}
