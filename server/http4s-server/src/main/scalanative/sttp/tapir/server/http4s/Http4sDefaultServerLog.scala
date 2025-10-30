package sttp.tapir.server.http4s

import cats.effect.Sync
import sttp.tapir.server.interceptor.log.DefaultServerLog

object Http4sDefaultServerLog {
  def apply[F[_]: Sync]: DefaultServerLog[F] = {
    DefaultServerLog(
      doLogWhenReceived = debugLog(_, None),
      doLogWhenHandled = debugLog[F],
      doLogAllDecodeFailures = debugLog[F],
      doLogExceptions = (msg: String, ex: Throwable) => debugLog(msg, Option(ex)),
      noLog = Sync[F].pure(())
    )
  }

  private def debugLog[F[_]](msg: String, exOpt: Option[Throwable])(implicit sync: Sync[F]): F[Unit] =
    exOpt match {
      case None     => Sync[F].delay(println(msg))
      case Some(ex) =>
        Sync[F].delay {
          println(msg)
          ex.printStackTrace()
        }
    }
}
