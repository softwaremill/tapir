package sttp.tapir.server.vertx

import io.vertx.core.logging.Logger
import sttp.tapir.TapirFile
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interceptor.Interceptor
import sttp.tapir.server.interceptor.log.{DefaultServerLog, ServerLog}

trait VertxServerOptions[F[_]] {
  def uploadDirectory: TapirFile
  def deleteFile: TapirFile => F[Unit]
  def interceptors: List[Interceptor[F]]
}

object VertxServerOptions {
  def defaultServerLog(log: Logger): ServerLog = DefaultServerLog(
    doLogWhenHandled = debugLog(log),
    doLogAllDecodeFailures = infoLog(log),
    doLogExceptions = (msg: String, ex: Throwable) => log.error(msg, ex)
  )

  private def debugLog(log: Logger)(msg: String, exOpt: Option[Throwable]): Unit =
    exOpt match {
      case None     => log.debug(msg, Nil: _*)
      case Some(ex) => log.debug(s"$msg; exception: {}", ex)
    }

  private def infoLog(log: Logger)(msg: String, exOpt: Option[Throwable]): Unit =
    exOpt match {
      case None     => log.info(msg, Nil: _*)
      case Some(ex) => log.info(s"$msg; exception: {}", ex)
    }
}
