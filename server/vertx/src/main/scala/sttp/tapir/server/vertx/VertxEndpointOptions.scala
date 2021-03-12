package sttp.tapir.server.vertx

import io.vertx.core.logging.Logger
import io.vertx.ext.web.RoutingContext
import sttp.tapir.server.interceptor.EndpointInterceptor
import sttp.tapir.server.interceptor.log.{DefaultServerLog, ServerLog}

import java.io.File

trait VertxEndpointOptions[F[_]] {
  def uploadDirectory: File
  def interceptors: List[EndpointInterceptor[F, RoutingContext => Unit]]
}

object VertxEndpointOptions {
  def defaultServerLog(log: Logger): ServerLog[Unit] = DefaultServerLog(
    doLogWhenHandled = debugLog(log),
    doLogAllDecodeFailures = infoLog(log),
    doLogExceptions = (msg: String, ex: Throwable) => log.error(msg, ex),
    noLog = ()
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
