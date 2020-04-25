package sttp.tapir.server.vertx

import java.io.File

import io.vertx.core.logging.{Logger, LoggerFactory}
import sttp.tapir.server.{DecodeFailureHandler, LogRequestHandling, ServerDefaults}

import scala.concurrent.ExecutionContext

case class VertxEndpointOptions(
  decodeFailureHandler: DecodeFailureHandler = ServerDefaults.decodeFailureHandler,
  logger: Logger = LoggerFactory.getLogger("tapir-vertx"),
  logRequestHandling: LogRequestHandling[Logger => Unit] = VertxEndpointOptions.defaultLogRequestHandling,
  uploadDirectory: File = File.createTempFile("tapir", null).getParentFile.getAbsoluteFile,
  private val specificExecutionContext: Option[ExecutionContext] = None
) {

  def executionContextOr(default: ExecutionContext): ExecutionContext =
    specificExecutionContext.getOrElse(default)

  def logWhenHandled(shouldLog: Boolean): VertxEndpointOptions =
    copy(logRequestHandling = logRequestHandling.copy(logWhenHandled = shouldLog))

  def logAllDecodeFailures(shouldLog: Boolean): VertxEndpointOptions =
    copy(logRequestHandling = logRequestHandling.copy(logAllDecodeFailures = shouldLog))

}

object VertxEndpointOptions {
  lazy val defaultLogRequestHandling: LogRequestHandling[Logger => Unit] = LogRequestHandling(
    doLogWhenHandled = debugLog,
    doLogAllDecodeFailures = infoLog,
    doLogLogicExceptions = (msg: String, ex: Throwable) => log => log.error(msg, ex),
    noLog = _ => ()
  )

  private def debugLog(msg: String, exOpt: Option[Throwable]): Logger => Unit = exOpt match {
    case None     => log => log.debug(msg, List():_*)
    case Some(ex) => log => log.debug(s"$msg; exception: {}", ex)
  }

  private def infoLog(msg: String, exOpt: Option[Throwable]): Logger => Unit = exOpt match {
    case None     => log => log.info(msg, List():_*)
    case Some(ex) => log => log.info(s"$msg; exception: {}", ex)
  }

}
