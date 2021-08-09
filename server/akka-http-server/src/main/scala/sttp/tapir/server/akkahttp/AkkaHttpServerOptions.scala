package sttp.tapir.server.akkahttp

import akka.event.LoggingAdapter
import akka.http.scaladsl.server.RequestContext
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interceptor.log.{DefaultServerLog, ServerLog, ServerLogInterceptor}
import sttp.tapir.server.interceptor.{CustomInterceptors, Interceptor}
import sttp.tapir.{Defaults, TapirFile}

import scala.concurrent.Future

case class AkkaHttpServerOptions(
    createFile: ServerRequest => Future[TapirFile],
    deleteFile: TapirFile => Future[Unit],
    interceptors: List[Interceptor[Future, AkkaResponseBody]]
) {
  def prependInterceptor(i: Interceptor[Future, AkkaResponseBody]): AkkaHttpServerOptions = copy(interceptors = i :: interceptors)
  def appendInterceptor(i: Interceptor[Future, AkkaResponseBody]): AkkaHttpServerOptions = copy(interceptors = interceptors :+ i)
}

object AkkaHttpServerOptions {

  /** Allows customising the interceptors used by the server interpreter. */
  def customInterceptors: CustomInterceptors[Future, AkkaResponseBody, LoggingAdapter => Future[Unit], AkkaHttpServerOptions] =
    CustomInterceptors(
      createLogInterceptor = Log.serverLogInterceptor,
      createOptions = (ci: CustomInterceptors[Future, AkkaResponseBody, LoggingAdapter => Future[Unit], AkkaHttpServerOptions]) =>
        AkkaHttpServerOptions(defaultCreateFile, defaultDeleteFile, ci.interceptors)
    ).serverLog(Log.defaultServerLog)

  val defaultCreateFile: ServerRequest => Future[TapirFile] = { _ =>
    import scala.concurrent.ExecutionContext.Implicits.global
    Future(Defaults.createTempFile())
  }

  val defaultDeleteFile: TapirFile => Future[Unit] = file => {
    import scala.concurrent.ExecutionContext.Implicits.global
    Future(Defaults.deleteFile()(file))
  }

  object Log {
    val defaultServerLog: ServerLog[LoggingAdapter => Future[Unit]] = DefaultServerLog(
      doLogWhenHandled = debugLog,
      doLogAllDecodeFailures = debugLog,
      doLogExceptions = (msg: String, ex: Throwable) => log => Future.successful(log.error(ex, msg)),
      noLog = _ => Future.successful(())
    )

    def serverLogInterceptor(
        serverLog: ServerLog[LoggingAdapter => Future[Unit]]
    ): ServerLogInterceptor[LoggingAdapter => Future[Unit], Future, AkkaResponseBody] =
      new ServerLogInterceptor[LoggingAdapter => Future[Unit], Future, AkkaResponseBody](
        serverLog,
        (f, request) => f(request.underlying.asInstanceOf[RequestContext].log)
      )

    private def debugLog(msg: String, exOpt: Option[Throwable]): LoggingAdapter => Future[Unit] = log =>
      Future.successful {
        exOpt match {
          case None     => log.debug(msg)
          case Some(ex) => log.debug(s"$msg; exception: {}", ex)
        }
      }
  }

  val default: AkkaHttpServerOptions = customInterceptors.options
}
