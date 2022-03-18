package sttp.tapir.server.vertx

import io.vertx.core.logging.{Logger, LoggerFactory}
import io.vertx.core.{Context, Vertx}
import io.vertx.ext.web.RoutingContext
import sttp.monad.{FutureMonad, MonadError}
import sttp.tapir.server.interceptor.log.{DefaultServerLog, ServerLog}
import sttp.tapir.server.interceptor.{CustomInterceptors, Interceptor}
import sttp.tapir.{Defaults, TapirFile}

import scala.concurrent.{ExecutionContext, Future}

final case class VertxFutureServerOptions(
    uploadDirectory: TapirFile,
    deleteFile: TapirFile => Future[Unit],
    interceptors: List[Interceptor[Future]],
    private val specificExecutionContext: Option[ExecutionContext]
) extends VertxServerOptions[Future] {
  def executionContextOr(default: ExecutionContext): ExecutionContext =
    specificExecutionContext.getOrElse(default)

  private[vertx] def executionContextOrCurrentCtx(rc: RoutingContext) =
    executionContextOr(new VertxExecutionContext(rc.vertx, rc.vertx.getOrCreateContext))

  def prependInterceptor(i: Interceptor[Future]): VertxFutureServerOptions =
    copy(interceptors = i :: interceptors)
  def appendInterceptor(i: Interceptor[Future]): VertxFutureServerOptions =
    copy(interceptors = interceptors :+ i)
}

object VertxFutureServerOptions {

  /** Allows customising the interceptors used by the server interpreter. */
  def customInterceptors: CustomInterceptors[Future, VertxFutureServerOptions] =
    CustomInterceptors(
      createOptions = (ci: CustomInterceptors[Future, VertxFutureServerOptions]) =>
        VertxFutureServerOptions(
          VertxServerOptions.uploadDirectory(),
          defaultDeleteFile,
          ci.interceptors,
          None
        )
    ).serverLog(defaultServerLog(LoggerFactory.getLogger("tapir-vertx")))

  val defaultDeleteFile: TapirFile => Future[Unit] = file => {
    import scala.concurrent.ExecutionContext.Implicits.global
    Future(Defaults.deleteFile()(file))
  }

  val default: VertxFutureServerOptions = customInterceptors.options

  def defaultServerLog(log: Logger): ServerLog[Future] = {
    import scala.concurrent.ExecutionContext.Implicits.global
    implicit val monadError: MonadError[Future] = new FutureMonad

    DefaultServerLog(
      doLogWhenReceived = debugLog(log)(_, None),
      doLogWhenHandled = debugLog(log),
      doLogAllDecodeFailures = infoLog(log),
      doLogExceptions = (msg: String, ex: Throwable) => Future.successful { log.error(msg, ex) },
      noLog = Future.successful(())
    )
  }

  private def debugLog(log: Logger)(msg: String, exOpt: Option[Throwable]): Future[Unit] = Future.successful {
    VertxServerOptions.debugLog(log)(msg, exOpt)
  }

  private def infoLog(log: Logger)(msg: String, exOpt: Option[Throwable]): Future[Unit] = Future.successful {
    VertxServerOptions.infoLog(log)(msg, exOpt)
  }
}

class VertxExecutionContext(val vertx: Vertx, val ctx: Context) extends ExecutionContext {
  override def execute(runnable: Runnable): Unit =
    if (vertx.getOrCreateContext() != ctx) {
      ctx.runOnContext((_: Void) => runnable.run())
    } else {
      runnable.run()
    }

  override def reportFailure(cause: Throwable): Unit =
    VertxExecutionContext.Log.error("Failed executing", cause)
}

object VertxExecutionContext {
  private[vertx] val Log = LoggerFactory.getLogger(classOf[VertxExecutionContext].getName)
}
