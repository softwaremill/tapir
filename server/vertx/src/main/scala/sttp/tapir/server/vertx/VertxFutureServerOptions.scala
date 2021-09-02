package sttp.tapir.server.vertx

import io.vertx.core.logging.LoggerFactory
import io.vertx.core.{Context, Vertx}
import io.vertx.ext.web.RoutingContext
import sttp.tapir.server.interceptor.log.{ServerLog, ServerLogInterceptor}
import sttp.tapir.server.interceptor.{CustomInterceptors, Interceptor}
import sttp.tapir.{Defaults, TapirFile}

import java.io.File
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
  def customInterceptors: CustomInterceptors[Future, Unit, VertxFutureServerOptions] =
    CustomInterceptors(
      createLogInterceptor = (sl: ServerLog[Unit]) => new ServerLogInterceptor[Unit, Future](sl, (_, _) => Future.successful(())),
      createOptions = (ci: CustomInterceptors[Future, Unit, VertxFutureServerOptions]) =>
        VertxFutureServerOptions(
          TapirFile.fromFile(File.createTempFile("tapir", null).getParentFile.getAbsoluteFile),
          defaultDeleteFile,
          ci.interceptors,
          None
        )
    ).serverLog(VertxServerOptions.defaultServerLog(LoggerFactory.getLogger("tapir-vertx")))

  val defaultDeleteFile: TapirFile => Future[Unit] = file => {
    import scala.concurrent.ExecutionContext.Implicits.global
    Future(Defaults.deleteFile()(file))
  }

  val default: VertxFutureServerOptions = customInterceptors.options
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
