package sttp.tapir.server.vertx.interpreters

import io.vertx.core.{Future, Handler}
import io.vertx.ext.web.{Route, Router, RoutingContext}
import sttp.capabilities.zio.ZioStreams
import sttp.monad.MonadError
import sttp.tapir.server.vertx.decoders.VertxInputDecoders.decodeBodyAndInputsThen
import sttp.tapir.server.vertx.handlers.tryEncodeError
import sttp.tapir.server.vertx.routing.PathMapping.extractRouteDefinition
import sttp.tapir.server.vertx.streams.zio._
import sttp.tapir.server.vertx.VertxEffectfulEndpointOptions
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.internal.Params
import sttp.tapir.Endpoint
import zio._

import scala.reflect.ClassTag

trait VertxZioServerInterpreter extends CommonServerInterpreter {

  def route[R, I, E, O](e: Endpoint[I, E, O, ZioStreams])(logic: I => ZIO[R, E, O])(implicit
      endpointOptions: VertxEffectfulEndpointOptions,
      runtime: Runtime[R]
  ): Router => Route =
    route(ServerEndpoint[I, E, O, ZioStreams, RIO[R, *]](e, _ => logic(_).either))

  def route[R, I, E, O](e: ServerEndpoint[I, E, O, ZioStreams, RIO[R, *]])(implicit
      endpointOptions: VertxEffectfulEndpointOptions,
      runtime: Runtime[R]
  ): Router => Route = { router =>
    implicit val ect: Option[ClassTag[E]] = None
    mountWithDefaultHandlers(e)(router, extractRouteDefinition(e.endpoint))
      .handler(endpointHandler(e)(e.logic, responseHandlerWithError(e)))
  }

  def routeRecoverErrors[R, I, E, O](e: Endpoint[I, E, O, ZioStreams])(
      logic: I => RIO[R, O]
  )(implicit
      endpointOptions: VertxEffectfulEndpointOptions,
      eIsThrowable: E <:< Throwable,
      eClassTag: ClassTag[E],
      runtime: Runtime[R]
  ): Router => Route =
    route(e.serverLogicRecoverErrors(logic))

  private def endpointHandler[R, I, E, O, A](e: ServerEndpoint[I, E, O, ZioStreams, RIO[R, *]])(
      logic: MonadError[RIO[R, *]] => I => RIO[R, A],
      responseHandler: (A, RoutingContext) => Unit
  )(implicit
      serverOptions: VertxEffectfulEndpointOptions,
      runtime: Runtime[R],
      ect: Option[ClassTag[E]]
  ): Handler[RoutingContext] = { rc =>
    decodeBodyAndInputsThen(
      e.endpoint,
      rc,
      logicHandler(e)(logic(monadError.asInstanceOf[MonadError[RIO[R, *]]]), responseHandler, rc)
    )
  }

  private def logicHandler[R, I, E, O, T](
      e: ServerEndpoint[I, E, O, ZioStreams, RIO[R, *]]
  )(logic: I => RIO[R, T], responseHandler: (T, RoutingContext) => Unit, rc: RoutingContext)(implicit
      serverOptions: VertxEffectfulEndpointOptions,
      runtime: Runtime[R],
      ect: Option[ClassTag[E]]
  ): Params => Unit = { params =>
    val canceler = runtime.unsafeRunAsyncCancelable(logic(params.asAny.asInstanceOf[I])) {
      case Exit.Success(result) =>
        responseHandler(result, rc)
      case Exit.Failure(cause) =>
        serverOptions.logRequestHandling.logicException(e.endpoint, cause.squash)(serverOptions.logger)
        tryEncodeError(e.endpoint, rc, cause.squash)
    }

    rc.response.exceptionHandler { _ =>
      canceler(Fiber.Id.None)
      ()
    }

    ()
  }

  private[vertx] val monadError = new MonadError[Task] {
    override def unit[T](t: T): Task[T] = Task.succeed(t)
    override def map[T, T2](fa: Task[T])(f: T => T2): Task[T2] = fa.map(f)
    override def flatMap[T, T2](fa: Task[T])(f: T => Task[T2]): Task[T2] = fa.flatMap(f)
    override def error[T](t: Throwable): Task[T] = Task.fail(t)
    override protected def handleWrappedError[T](rt: Task[T])(h: PartialFunction[Throwable, Task[T]]): Task[T] =
      rt.catchSome(h)
    override def eval[T](t: => T): Task[T] = Task.effect(t)
    override def suspend[T](t: => Task[T]): Task[T] = Task.effectSuspend(t)
    override def flatten[T](ffa: Task[Task[T]]): Task[T] = ffa.flatten
    override def ensure[T](f: Task[T], e: => Task[Unit]): Task[T] = f.ensuring(e.catchAll(_ => Task.unit))
  }

  implicit class VertxFutureForZio[A](future: => Future[A]) {

    def asTask: Task[A] =
      Task.effectAsync { cb =>
        future.onComplete { handler =>
          if (handler.succeeded()) {
            cb(Task.succeed(handler.result()))
          } else {
            cb(Task.fail(handler.cause()))
          }
        }
      }
  }
}
