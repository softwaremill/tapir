package sttp.tapir.server.play

import akka.stream.Materializer
import play.api.Logger
import play.api.http.HttpEntity
import play.api.libs.Files.{SingletonTemporaryFileCreator, TemporaryFileCreator}
import play.api.mvc._
import sttp.tapir.server.interceptor.Interceptor
import sttp.tapir.server.interceptor.content.UnsupportedMediaTypeInterceptor
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureHandler, DecodeFailureInterceptor, DefaultDecodeFailureHandler}
import sttp.tapir.server.interceptor.exception.{DefaultExceptionHandler, ExceptionHandler, ExceptionInterceptor}
import sttp.tapir.server.interceptor.log.{DefaultServerLog, ServerLog, ServerLogInterceptor}
import sttp.tapir.{Defaults, TapirFile}
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor

import scala.concurrent.{ExecutionContext, Future}

case class PlayServerOptions(
    temporaryFileCreator: TemporaryFileCreator,
    deleteFile: TapirFile => Future[Unit],
    defaultActionBuilder: ActionBuilder[Request, AnyContent],
    playBodyParsers: PlayBodyParsers,
    decodeFailureHandler: DecodeFailureHandler,
    interceptors: List[Interceptor[Future, HttpEntity]]
) {
  def prependInterceptor(i: Interceptor[Future, HttpEntity]): PlayServerOptions = copy(interceptors = i :: interceptors)
  def appendInterceptor(i: Interceptor[Future, HttpEntity]): PlayServerOptions = copy(interceptors = interceptors :+ i)
}

object PlayServerOptions {

  /** Creates default [[PlayServerOptions]] with custom interceptors, sitting between two interceptor groups:
    * 1. the optional exception interceptor and the optional logging interceptor (which should typically be first
    *    when processing the request, and last when processing the response)),
    * 2. the optional unsupported media type interceptor and the decode failure handling interceptor (which should
    *    typically be last when processing the request).
    *
    * The options can be then further customised using copy constructors or the methods to append/prepend
    * interceptors.
    *
    * @param exceptionHandler Whether to respond to exceptions, or propagate them to play.
    * @param serverLog The server log using which an interceptor will be created, if any.
    * @param additionalInterceptors Additional interceptors, e.g. handling decode failures, or providing alternate
    *                               responses.
    * @param unsupportedMediaTypeInterceptor Whether to return 415 (unsupported media type) if there's no body in the
    *                                        endpoint's outputs, which can satisfy the constraints from the `Accept`
    *                                        header
    * @param decodeFailureHandler The decode failure handler, from which an interceptor will be created.
    */
  def customInterceptors(
      metricsInterceptor: Option[MetricsRequestInterceptor[Future, HttpEntity]] = None,
      exceptionHandler: Option[ExceptionHandler] = Some(DefaultExceptionHandler),
      serverLog: Option[ServerLog[Unit]] = Some(defaultServerLog),
      additionalInterceptors: List[Interceptor[Future, HttpEntity]] = Nil,
      unsupportedMediaTypeInterceptor: Option[UnsupportedMediaTypeInterceptor[Future, HttpEntity]] = Some(
        new UnsupportedMediaTypeInterceptor()
      ),
      decodeFailureHandler: DecodeFailureHandler = DefaultDecodeFailureHandler.handler
  )(implicit mat: Materializer, ec: ExecutionContext): PlayServerOptions =
    PlayServerOptions(
      SingletonTemporaryFileCreator,
      defaultDeleteFile,
      DefaultActionBuilder.apply(PlayBodyParsers.apply().anyContent),
      PlayBodyParsers.apply(),
      decodeFailureHandler,
      metricsInterceptor.toList ++
        exceptionHandler.map(new ExceptionInterceptor[Future, HttpEntity](_)).toList ++
        serverLog.map(new ServerLogInterceptor[Unit, Future, HttpEntity](_, (_, _) => Future.successful(()))).toList ++
        additionalInterceptors ++
        unsupportedMediaTypeInterceptor.toList ++
        List(new DecodeFailureInterceptor[Future, HttpEntity](decodeFailureHandler))
    )

  val defaultDeleteFile: TapirFile => Future[Unit] = file => {
    import scala.concurrent.ExecutionContext.Implicits.global
    Future(Defaults.deleteFile()(file))
  }

  lazy val defaultServerLog: ServerLog[Unit] = DefaultServerLog(
    doLogWhenHandled = debugLog,
    doLogAllDecodeFailures = debugLog,
    doLogExceptions = (msg: String, ex: Throwable) => logger.error(msg, ex),
    noLog = ()
  )

  private def debugLog(msg: String, exOpt: Option[Throwable]): Unit =
    exOpt match {
      case None     => logger.debug(msg)
      case Some(ex) => logger.debug(s"$msg; exception: {}", ex)
    }

  implicit def default(implicit mat: Materializer, ec: ExecutionContext): PlayServerOptions = customInterceptors()

  lazy val logger: Logger = Logger(this.getClass.getPackage.getName)
}
