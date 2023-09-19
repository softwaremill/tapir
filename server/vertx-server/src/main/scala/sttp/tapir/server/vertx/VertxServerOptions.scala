package sttp.tapir.server.vertx

import io.vertx.core.logging.Logger
import sttp.tapir.TapirFile
import sttp.tapir.server.interceptor.Interceptor

trait VertxServerOptions[F[_]] {
  def uploadDirectory: TapirFile
  def deleteFile: TapirFile => F[Unit]
  def interceptors: List[Interceptor[F]]
}

object VertxServerOptions {
  private[vertx] def debugLog(log: Logger)(msg: String, exOpt: Option[Throwable]): Unit =
    exOpt match {
      case None     => log.debug(msg, Nil: _*)
      case Some(ex) => log.debug(s"$msg; exception: {}", ex)
    }

  private[vertx] def infoLog(log: Logger)(msg: String, exOpt: Option[Throwable]): Unit =
    exOpt match {
      case None     => log.info(msg, Nil: _*)
      case Some(ex) => log.info(s"$msg; exception: {}", ex)
    }

  private[vertx] def uploadDirectory(): TapirFile =
    new java.io.File(System.getProperty("java.io.tmpdir")).getAbsoluteFile
}
