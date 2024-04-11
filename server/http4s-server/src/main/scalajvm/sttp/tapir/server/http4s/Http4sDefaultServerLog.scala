package sttp.tapir.server.http4s

import cats.effect.Sync
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import sttp.tapir.server.interceptor.log.DefaultServerLog

object Http4sDefaultServerLog {

  def apply[F[_]: Sync]: DefaultServerLog[F] = {
    val log = Slf4jLogger.getLogger[F]
    DefaultServerLog(
      doLogWhenReceived = msg => debugLog(log)(msg, None),
      doLogWhenHandled = debugLog(log),
      doLogAllDecodeFailures = debugLog(log),
      doLogExceptions = (msg: String, ex: Throwable) => log.error(ex)(msg),
      noLog = Sync[F].pure(())
    )
  }

  private def debugLog[F[_]](log: Logger[F])(msg: String, exOpt: Option[Throwable]): F[Unit] =
    exOpt match {
      case None     => log.debug(msg)
      case Some(ex) => log.debug(ex)(msg)
    }
}
