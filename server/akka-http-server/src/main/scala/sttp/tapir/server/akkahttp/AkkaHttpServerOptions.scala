package sttp.tapir.server.akkahttp

import java.io.File

import akka.http.scaladsl.server.RequestContext
import sttp.tapir.Defaults
import sttp.tapir.Defaults
import sttp.tapir.server.{DecodeFailureHandler, LoggingOptions, ServerDefaults}

import scala.concurrent.Future

case class AkkaHttpServerOptions(
    createFile: RequestContext => Future[File],
    decodeFailureHandler: DecodeFailureHandler[RequestContext],
    loggingOptions: LoggingOptions
)

object AkkaHttpServerOptions {
  val defaultCreateFile: RequestContext => Future[File] = { _ =>
    import scala.concurrent.ExecutionContext.Implicits.global
    Future(Defaults.createTempFile())
  }

  implicit val default: AkkaHttpServerOptions =
    AkkaHttpServerOptions(defaultCreateFile, ServerDefaults.decodeFailureHandler, LoggingOptions.default)
}
