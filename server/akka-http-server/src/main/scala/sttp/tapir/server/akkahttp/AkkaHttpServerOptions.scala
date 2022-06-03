package sttp.tapir.server.akkahttp

import akka.event.LoggingAdapter
import org.slf4j.LoggerFactory
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interceptor._
import sttp.tapir.server.interceptor.log.{DefaultServerLog, ServerLog}
import sttp.tapir.{Defaults, TapirFile}

import scala.annotation.nowarn
import scala.concurrent.{ExecutionContext, Future, blocking}

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
  def customiseInterceptors(implicit
      ec: ExecutionContext
  ): CustomiseInterceptors[Future, AkkaHttpServerOptions] =
    CustomiseInterceptors(
      createOptions = (ci: CustomiseInterceptors[Future, AkkaHttpServerOptions]) =>
        AkkaHttpServerOptions(defaultCreateFile(_), defaultDeleteFile(_), ci.interceptors)
    ).serverLog(defaultSlf4jServerLog)

  def defaultCreateFile(@nowarn r: ServerRequest)(implicit ec: ExecutionContext): Future[TapirFile] = {
    Future(blocking(Defaults.createTempFile()))
  }

  def defaultDeleteFile(file: TapirFile)(implicit ec: ExecutionContext): Future[Unit] = {
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
      doLogWhenReceived = debugLog(_, None),
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
      doLogWhenReceived = debugLog(_, None),
      doLogWhenHandled = debugLog,
      doLogAllDecodeFailures = debugLog,
      doLogExceptions = errorLog,
      noLog = Future.successful(())
    )
  }

  def default(implicit ec: ExecutionContext): AkkaHttpServerOptions = customiseInterceptors.options
}
