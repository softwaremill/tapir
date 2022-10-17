package sttp.tapir.server.http4s

import cats.effect.Sync
import sttp.tapir.server.interceptor.log.DefaultServerLog
import org.log4s.{Logger, getLogger}

object Http4sDefaultServerLog {
  private[http4s] val log: Logger = getLogger

  def apply[F[_]: Sync]: DefaultServerLog[F] = {
    DefaultServerLog(
      doLogWhenReceived = debugLog(_, None),
      doLogWhenHandled = debugLog[F],
      doLogAllDecodeFailures = debugLog[F],
      doLogExceptions = (msg: String, ex: Throwable) => Sync[F].delay(log.error(ex)(msg)),
      noLog = Sync[F].pure(())
    )
  }

  private def debugLog[F[_]](msg: String, exOpt: Option[Throwable])(implicit sync: Sync[F]): F[Unit] =
    exOpt match {
      case None     => Sync[F].delay(log.debug(msg))
      case Some(ex) => Sync[F].delay(log.debug(ex)(msg))
    }
}
