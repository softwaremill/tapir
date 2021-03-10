package sttp.tapir.server.akkahttp

import java.io.File
import akka.event.LoggingAdapter
import akka.http.scaladsl.server.RequestContext
import sttp.tapir.Defaults
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interceptor.{DecodeFailureInterceptor, EndpointInterceptor, LogInterceptor}
import sttp.tapir.server.{DecodeFailureHandler, DefaultLogRequestHandling, LogRequestHandling, ServerDefaults}

import scala.concurrent.Future

case class AkkaHttpServerOptions(
    createFile: ServerRequest => Future[File],
    interceptors: List[EndpointInterceptor[Future, AkkaResponseBody]]
) {
  def prependInterceptor(i: EndpointInterceptor[Future, AkkaResponseBody]): AkkaHttpServerOptions = copy(interceptors = i :: interceptors)
  def appendInterceptor(i: EndpointInterceptor[Future, AkkaResponseBody]): AkkaHttpServerOptions = copy(interceptors = interceptors :+ i)
}

object AkkaHttpServerOptions {
  def default(
      logRequestHandling: LogRequestHandling[LoggingAdapter => Future[Unit]] = defaultLogRequestHandling,
      decodeFailureHandler: DecodeFailureHandler = ServerDefaults.decodeFailureHandler
  ): AkkaHttpServerOptions =
    AkkaHttpServerOptions(
      defaultCreateFile,
      List(
        logInterceptor(logRequestHandling),
        new DecodeFailureInterceptor(decodeFailureHandler)
      )
    )

  lazy val defaultCreateFile: ServerRequest => Future[File] = { _ =>
    import scala.concurrent.ExecutionContext.Implicits.global
    Future(Defaults.createTempFile())
  }

  val defaultLogRequestHandling: LogRequestHandling[LoggingAdapter => Future[Unit]] = DefaultLogRequestHandling(
    doLogWhenHandled = debugLog,
    doLogAllDecodeFailures = debugLog,
    doLogLogicExceptions = (msg: String, ex: Throwable) => log => Future.successful(log.error(ex, msg)),
    noLog = _ => Future.successful(())
  )

  def logInterceptor(
      logRequestHandling: LogRequestHandling[LoggingAdapter => Future[Unit]]
  ): LogInterceptor[LoggingAdapter => Future[Unit], Future, AkkaResponseBody] =
    new LogInterceptor[LoggingAdapter => Future[Unit], Future, AkkaResponseBody](
      logRequestHandling,
      (f, request) => f(request.underlying.asInstanceOf[RequestContext].log)
    )

  implicit val defaultOptions: AkkaHttpServerOptions = default()

  private def debugLog(msg: String, exOpt: Option[Throwable]): LoggingAdapter => Future[Unit] = log =>
    Future.successful {
      exOpt match {
        case None     => log.debug(msg)
        case Some(ex) => log.debug(s"$msg; exception: {}", ex)
      }
    }
}
