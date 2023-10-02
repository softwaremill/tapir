package sttp.tapir.server.pekkohttp

import java.io.File

import org.apache.pekko.event.LoggingAdapter
import org.apache.pekko.http.scaladsl.server.RequestContext
import sttp.tapir.Defaults
import sttp.tapir.server.{DecodeFailureHandler, LogRequestHandling, ServerDefaults}

import scala.concurrent.Future

case class PekkoHttpServerOptions(
    createFile: RequestContext => Future[File],
    decodeFailureHandler: DecodeFailureHandler,
    logRequestHandling: LogRequestHandling[LoggingAdapter => Unit]
)

object PekkoHttpServerOptions {
  implicit lazy val default: PekkoHttpServerOptions =
    PekkoHttpServerOptions(
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

  private def debugLog(msg: String, exOpt: Option[Throwable]): LoggingAdapter => Unit =
    exOpt match {
      case None     => log => log.debug(msg)
      case Some(ex) => log => log.debug(s"$msg; exception: {}", ex)
    }
}
