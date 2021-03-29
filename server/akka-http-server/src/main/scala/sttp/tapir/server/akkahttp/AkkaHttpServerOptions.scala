package sttp.tapir.server.akkahttp

import java.io.File
import akka.event.LoggingAdapter
import akka.http.scaladsl.server.RequestContext
import sttp.tapir.Defaults
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interceptor.log.{DefaultServerLog, ServerLog, ServerLogInterceptor}
import sttp.tapir.server.interceptor.EndpointInterceptor
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureHandler, DecodeFailureInterceptor, DefaultDecodeFailureHandler}
import sttp.tapir.server.interceptor.exception.{DefaultExceptionHandler, ExceptionHandler, ExceptionInterceptor}

import scala.concurrent.Future

case class AkkaHttpServerOptions(
    createFile: ServerRequest => Future[File],
    interceptors: List[EndpointInterceptor[Future, AkkaResponseBody]]
) {
  def prependInterceptor(i: EndpointInterceptor[Future, AkkaResponseBody]): AkkaHttpServerOptions = copy(interceptors = i :: interceptors)
  def appendInterceptor(i: EndpointInterceptor[Future, AkkaResponseBody]): AkkaHttpServerOptions = copy(interceptors = interceptors :+ i)
}

object AkkaHttpServerOptions {

  /** Creates default [[AkkaHttpServerOptions]] with custom interceptors, sitting between an optional exception
    * interceptor, optional logging interceptor, and the ultimate decode failure handling interceptor.
    *
    * The options can be then further customised using copy constructors or the methods to append/prepend
    * interceptors.
    *
    * @param exceptionHandler Whether to respond to exceptions, or propagate them to akka http.
    * @param serverLog The server log using which an interceptor will be created, if any.
    * @param additionalInterceptors Additional interceptors, e.g. handling decode failures, or providing alternate
    *                               responses.
    * @param decodeFailureHandler The decode failure handler, from which an interceptor will be created.
    */
  def customInterceptors(
      exceptionHandler: Option[ExceptionHandler] = Some(DefaultExceptionHandler),
      serverLog: Option[ServerLog[LoggingAdapter => Future[Unit]]] = Some(Log.defaultServerLog),
      additionalInterceptors: List[EndpointInterceptor[Future, AkkaResponseBody]] = Nil,
      decodeFailureHandler: DecodeFailureHandler = DefaultDecodeFailureHandler.handler
  ): AkkaHttpServerOptions =
    AkkaHttpServerOptions(
      defaultCreateFile,
      exceptionHandler.map(new ExceptionInterceptor[Future, AkkaResponseBody](_)).toList ++
        serverLog.map(Log.serverLogInterceptor).toList ++
        additionalInterceptors ++
        List(new DecodeFailureInterceptor[Future, AkkaResponseBody](decodeFailureHandler))
    )

  val defaultCreateFile: ServerRequest => Future[File] = { _ =>
    import scala.concurrent.ExecutionContext.Implicits.global
    Future(Defaults.createTempFile())
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

  implicit val default: AkkaHttpServerOptions = customInterceptors()
}
