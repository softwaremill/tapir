package sttp.tapir.server.vertx

import java.io.File
import io.vertx.core.Context
import io.vertx.core.Vertx
import io.vertx.core.logging.{Logger, LoggerFactory}
import io.vertx.ext.web.RoutingContext
import sttp.tapir.server.DecodeFailureHandler
import sttp.tapir.server.interceptor.decodefailure.ServerDefaults

import scala.concurrent.ExecutionContext

sealed trait VertxEndpointOptions {

  def decodeFailureHandler: DecodeFailureHandler

  def logger: Logger

  def logRequestHandling: LogRequestHandling[Logger => Unit]

  def uploadDirectory: File

  type Self

  protected def updateLogRequestHandling(logRequestHandling: LogRequestHandling[Logger => Unit]): Self

  def logWhenHandled(shouldLog: Boolean): Self =
    updateLogRequestHandling(logRequestHandling.copy(logWhenHandled = shouldLog))

  def logAllDecodeFailures(shouldLog: Boolean): Self =
    updateLogRequestHandling(logRequestHandling.copy(logAllDecodeFailures = shouldLog))

  def logLogicExceptions(shouldLog: Boolean): Self =
    updateLogRequestHandling(logRequestHandling.copy(logLogicExceptions = shouldLog))
}

final case class VertxFutureEndpointOptions(
    decodeFailureHandler: DecodeFailureHandler = ServerDefaults.decodeFailureHandler,
    logger: Logger = LoggerFactory.getLogger("tapir-vertx"),
    logRequestHandling: LogRequestHandling[Logger => Unit] = VertxEndpointOptions.defaultLogRequestHandling,
    uploadDirectory: File = File.createTempFile("tapir", null).getParentFile.getAbsoluteFile,
    private val specificExecutionContext: Option[ExecutionContext] = None
) extends VertxEndpointOptions {

  override type Self = VertxFutureEndpointOptions

  override protected def updateLogRequestHandling(logRequestHandling: LogRequestHandling[Logger => Unit]) =
    this.copy(logRequestHandling = logRequestHandling)

  def executionContextOr(default: ExecutionContext): ExecutionContext =
    specificExecutionContext.getOrElse(default)

  private[vertx] def executionContextOrCurrentCtx(rc: RoutingContext) =
    executionContextOr(new VertxExecutionContext(rc.vertx, rc.vertx.getOrCreateContext))
}

final case class VertxEffectfulEndpointOptions(
    decodeFailureHandler: DecodeFailureHandler = ServerDefaults.decodeFailureHandler,
    logger: Logger = LoggerFactory.getLogger("tapir-vertx"),
    logRequestHandling: LogRequestHandling[Logger => Unit] = VertxEndpointOptions.defaultLogRequestHandling,
    uploadDirectory: File = File.createTempFile("tapir", null).getParentFile.getAbsoluteFile,
    maxQueueSizeForReadStream: Int = 16
) extends VertxEndpointOptions {

  override type Self = VertxEffectfulEndpointOptions

  override protected def updateLogRequestHandling(logRequestHandling: LogRequestHandling[Logger => Unit]) =
    this.copy(logRequestHandling = logRequestHandling)
}

object VertxEndpointOptions {
  lazy val defaultLogRequestHandling: LogRequestHandling[Logger => Unit] = LogRequestHandling(
    doLogWhenHandled = debugLog,
    doLogAllDecodeFailures = infoLog,
    doLogLogicExceptions = (msg: String, ex: Throwable) => log => log.error(msg, ex),
    noLog = _ => ()
  )

  private def debugLog(msg: String, exOpt: Option[Throwable]): Logger => Unit =
    exOpt match {
      case None     => log => log.debug(msg, Nil: _*)
      case Some(ex) => log => log.debug(s"$msg; exception: {}", ex)
    }

  private def infoLog(msg: String, exOpt: Option[Throwable]): Logger => Unit =
    exOpt match {
      case None     => log => log.info(msg, Nil: _*)
      case Some(ex) => log => log.info(s"$msg; exception: {}", ex)
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
    VertxExecutionContext.Log.error("Failed executing on contet", cause)
}

object VertxExecutionContext {

  private[vertx] val Log = LoggerFactory.getLogger(classOf[VertxExecutionContext].getName)
}
