package sttp.tapir.server.sttpStub

import cats.Applicative
import sttp.tapir.server.{DecodeFailureHandler, LogRequestHandling, ServerDefaults}
import cats.effect._
import org.log4s._

case class SttpServerOptions[F[_]](
    decodeFailureHandler: DecodeFailureHandler,
    logRequestHandling: LogRequestHandling[F[Unit]]
)

object SttpServerOptions {
  implicit def default[F[_]: Sync]: SttpServerOptions[F] =
    SttpServerOptions[F](
      ServerDefaults.decodeFailureHandler,
      defaultLogRequestHandling[F]
    )

  def defaultLogRequestHandling[F[_]: Sync]: LogRequestHandling[F[Unit]] = LogRequestHandling[F[Unit]](
    doLogWhenHandled = debugLog[F],
    doLogAllDecodeFailures = debugLog[F],
    doLogLogicExceptions = (msg: String, ex: Throwable) => Sync[F].delay(log.error(ex)(msg)),
    noLog = Applicative[F].unit
  )

  private def debugLog[F[_]: Sync](msg: String, exOpt: Option[Throwable]): F[Unit] = exOpt match {
    case None     => Sync[F].delay(log.debug(msg))
    case Some(ex) => Sync[F].delay(log.debug(ex)(msg))
  }

  private[http4s] val log: Logger = getLogger
}
