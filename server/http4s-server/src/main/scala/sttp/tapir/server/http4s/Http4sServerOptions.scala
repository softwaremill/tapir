package sttp.tapir.server.http4s

import java.io.File

import cats.Applicative
import cats.effect.{ContextShift, Sync}
import org.http4s.Request
import sttp.tapir.Defaults
import sttp.tapir.server.{DecodeFailureHandler, LogRequestHandling, ServerDefaults}

import scala.concurrent.ExecutionContext

case class Http4sServerOptions[F[_]](
    createFile: (ExecutionContext, Request[F]) => F[File], // TODO: include request/part headers, information if this is a part?
    blockingExecutionContext: ExecutionContext,
    ioChunkSize: Int,
    decodeFailureHandler: DecodeFailureHandler,
    logRequestHandling: LogRequestHandling[F[Unit]]
)

object Http4sServerOptions {
  implicit def default[F[_]: Sync: ContextShift]: Http4sServerOptions[F] =
    Http4sServerOptions(
      defaultCreateFile,
      ExecutionContext.Implicits.global,
      8192,
      ServerDefaults.decodeFailureHandler,
      defaultLogRequestHandling[F]
    )

  def defaultCreateFile[F[_]](implicit sync: Sync[F], cs: ContextShift[F]): (ExecutionContext, Request[F]) => F[File] =
    (ec, _) => cs.evalOn(ec)(sync.delay(Defaults.createTempFile()))

  def defaultLogRequestHandling[F[_]: Sync]: LogRequestHandling[F[Unit]] = LogRequestHandling[F[Unit]](
    doLogWhenHandled = debugLog[F],
    doLogAllDecodeFailures = debugLog[F],
    doLogLogicExceptions = (msg: String, ex: Throwable) => Sync[F].delay(EndpointToHttp4sServer.log.error(ex)(msg)),
    noLog = Applicative[F].unit
  )

  private def debugLog[F[_]: Sync](msg: String, exOpt: Option[Throwable]): F[Unit] = exOpt match {
    case None     => Sync[F].delay(EndpointToHttp4sServer.log.debug(msg))
    case Some(ex) => Sync[F].delay(EndpointToHttp4sServer.log.debug(ex)(msg))
  }
}
