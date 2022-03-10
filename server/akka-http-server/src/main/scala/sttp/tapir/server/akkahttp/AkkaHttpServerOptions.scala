package sttp.tapir.server.akkahttp

import akka.event.LoggingAdapter
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interceptor._
import sttp.tapir.server.interceptor.log.{DefaultServerLog, ServerLog}
import sttp.tapir.{Defaults, TapirFile}
import org.slf4j.LoggerFactory

import scala.concurrent.{Future, blocking}

case class AkkaHttpServerOptions(
    createFile: ServerRequest => Future[TapirFile],
    deleteFile: TapirFile => Future[Unit],
    interceptors: List[Interceptor[Future]]
) {
  def prependInterceptor(i: Interceptor[Future]): AkkaHttpServerOptions = copy(interceptors = i :: interceptors)
  def appendInterceptor(i: Interceptor[Future]): AkkaHttpServerOptions = copy(interceptors = interceptors :+ i)
}

object AkkaHttpServerOptions {

  /** Allows customising the interceptors used by the server interpreter. */
  def customInterceptors: CustomInterceptors[Future, AkkaHttpServerOptions] =
    CustomInterceptors(
      createOptions = (ci: CustomInterceptors[Future, AkkaHttpServerOptions]) =>
        AkkaHttpServerOptions(defaultCreateFile, defaultDeleteFile, ci.interceptors)
    ).serverLog(defaultSlf4jServerLog)

  val defaultCreateFile: ServerRequest => Future[TapirFile] = { _ =>
    import scala.concurrent.ExecutionContext.Implicits.global
    Future(blocking(Defaults.createTempFile()))
  }

  val defaultDeleteFile: TapirFile => Future[Unit] = file => {
    import scala.concurrent.ExecutionContext.Implicits.global
    Future(blocking(Defaults.deleteFile()(file)))
  }

  val defaultSlf4jServerLog: ServerLog[Future] = {
    val log = LoggerFactory.getLogger(AkkaHttpServerInterpreter.getClass)

    def debugLog(msg: String, exOpt: Option[Throwable]): Future[Unit] = Future.successful {
      exOpt match {
        case None     => log.debug(msg)
        case Some(ex) => log.debug(s"$msg; exception: {}", ex)
      }
    }

    def errorLog(msg: String, ex: Throwable): Future[Unit] = Future.successful(log.error(msg, ex))

    DefaultServerLog[Future](
      doLogWhenHandled = debugLog,
      doLogAllDecodeFailures = debugLog,
      doLogExceptions = errorLog,
      noLog = Future.successful(())
    )
  }

  def defaultServerLog(loggingAdapter: LoggingAdapter): DefaultServerLog[Future] = {
    def debugLog(msg: String, exOpt: Option[Throwable]): Future[Unit] = Future.successful {
      exOpt match {
        case None     => loggingAdapter.debug(msg)
        case Some(ex) => loggingAdapter.debug(s"$msg; exception: {}", ex)
      }
    }

    def errorLog(msg: String, ex: Throwable): Future[Unit] = Future.successful {
      loggingAdapter.error(ex, msg)
    }

    DefaultServerLog[Future](
      doLogWhenHandled = debugLog,
      doLogAllDecodeFailures = debugLog,
      doLogExceptions = errorLog,
      noLog = Future.successful(())
    )
  }

  val default: AkkaHttpServerOptions = customInterceptors.options
}
