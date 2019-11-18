package sttp.tapir.server.akkahttp

import java.io.File

import akka.event.LoggingAdapter
import akka.http.scaladsl.server.RequestContext
import sttp.tapir.Defaults
import sttp.tapir.server.{DecodeFailureHandler, LogRequestHandling, ServerDefaults}

import scala.concurrent.Future

case class AkkaHttpServerOptions(
    createFile: RequestContext => Future[File],
    decodeFailureHandler: DecodeFailureHandler,
    logRequestHandling: LogRequestHandling[LoggingAdapter => Unit]
)

object AkkaHttpServerOptions {
  implicit lazy val default: AkkaHttpServerOptions =
    AkkaHttpServerOptions(
      defaultCreateFile,
      ServerDefaults.decodeFailureHandler,
      defaultLogRequestHandling
    )

  lazy val defaultCreateFile: RequestContext => Future[File] = { _ =>
    import scala.concurrent.ExecutionContext.Implicits.global
    Future(Defaults.createTempFile())
  }

  lazy val defaultLogRequestHandling: LogRequestHandling[LoggingAdapter => Unit] = LogRequestHandling(
    doLogWhenHandled = debugLog,
    doLogAllDecodeFailures = debugLog,
    doLogLogicExceptions = (msg: String, ex: Throwable) => log => log.error(ex, msg),
    noLog = _ => ()
  )

  private def debugLog(msg: String, exOpt: Option[Throwable]): LoggingAdapter => Unit = exOpt match {
    case None     => log => log.debug(msg)
    case Some(ex) => log => log.debug(s"$msg; exception: {}", ex)
  }
}
