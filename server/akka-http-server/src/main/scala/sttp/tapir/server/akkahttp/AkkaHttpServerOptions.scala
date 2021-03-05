package sttp.tapir.server.akkahttp

import java.io.File
import akka.event.LoggingAdapter
import sttp.tapir.Defaults
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.{DecodeFailureHandler, LogRequestHandling, ServerDefaults}

import scala.concurrent.Future

case class AkkaHttpServerOptions(
    createFile: ServerRequest => Future[File],
    decodeFailureHandler: DecodeFailureHandler,
    logRequestHandling: LoggingAdapter => LogRequestHandling[Future[Unit]]
)

object AkkaHttpServerOptions {
  implicit lazy val default: AkkaHttpServerOptions =
    AkkaHttpServerOptions(
      defaultCreateFile,
      ServerDefaults.decodeFailureHandler,
      defaultLogRequestHandling
    )

  lazy val defaultCreateFile: ServerRequest => Future[File] = { _ =>
    import scala.concurrent.ExecutionContext.Implicits.global
    Future(Defaults.createTempFile())
  }

  def defaultLogRequestHandling(log: LoggingAdapter): LogRequestHandling[Future[Unit]] = LogRequestHandling(
    doLogWhenHandled = debugLog(log),
    doLogAllDecodeFailures = debugLog(log),
    doLogLogicExceptions = (msg: String, ex: Throwable) => Future.successful(log.error(ex, msg)),
    noLog = Future.successful(())
  )

  private def debugLog(log: LoggingAdapter)(msg: String, exOpt: Option[Throwable]): Future[Unit] = Future.successful {
    exOpt match {
      case None     => log.debug(msg)
      case Some(ex) => log.debug(s"$msg; exception: {}", ex)
    }
  }
}
