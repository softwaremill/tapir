package sttp.tapir.server.play

import akka.stream.Materializer
import play.api.Logger
import play.api.http.HttpEntity
import play.api.libs.Files.{SingletonTemporaryFileCreator, TemporaryFileCreator}
import play.api.mvc._
import sttp.tapir.server.interceptor.EndpointInterceptor
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureHandler, DecodeFailureInterceptor, DefaultDecodeFailureHandler}
import sttp.tapir.server.interceptor.exception.{DefaultExceptionHandler, ExceptionHandler, ExceptionInterceptor}
import sttp.tapir.server.interceptor.log.{DefaultServerLog, ServerLog, ServerLogInterceptor}

import scala.concurrent.{ExecutionContext, Future}

case class PlayServerOptions(
    temporaryFileCreator: TemporaryFileCreator,
    defaultActionBuilder: ActionBuilder[Request, AnyContent],
    playBodyParsers: PlayBodyParsers,
    decodeFailureHandler: DecodeFailureHandler,
    interceptors: List[EndpointInterceptor[Future, HttpEntity]]
) {
  def prependInterceptor(i: EndpointInterceptor[Future, HttpEntity]): PlayServerOptions = copy(interceptors = i :: interceptors)
  def appendInterceptor(i: EndpointInterceptor[Future, HttpEntity]): PlayServerOptions = copy(interceptors = interceptors :+ i)
}

object PlayServerOptions {

  /** Creates default [[PlayServerOptions]] with custom interceptors, sitting between an optional exception
    * interceptor, optional logging interceptor, and the ultimate decode failure handling interceptor.
    *
    * The options can be then further customised using copy constructors or the methods to append/prepend
    * interceptors.
    *
    * @param exceptionHandler Whether to respond to exceptions, or propagate them to play.
    * @param serverLog The server log using which an interceptor will be created, if any.
    * @param additionalInterceptors Additional interceptors, e.g. handling decode failures, or providing alternate
    *                               responses.
    * @param decodeFailureHandler The decode failure handler, from which an interceptor will be created.
    */
  def customInterceptors(
      exceptionHandler: Option[ExceptionHandler] = Some(DefaultExceptionHandler),
      serverLog: Option[ServerLog[Unit]] = Some(defaultServerLog),
      additionalInterceptors: List[EndpointInterceptor[Future, HttpEntity]] = Nil,
      decodeFailureHandler: DecodeFailureHandler = DefaultDecodeFailureHandler.handler
  )(implicit mat: Materializer, ec: ExecutionContext): PlayServerOptions =
    PlayServerOptions(
      SingletonTemporaryFileCreator,
      DefaultActionBuilder.apply(PlayBodyParsers.apply().anyContent),
      PlayBodyParsers.apply(),
      decodeFailureHandler,
      exceptionHandler.map(new ExceptionInterceptor[Future, HttpEntity](_)).toList ++
        serverLog.map(new ServerLogInterceptor[Unit, Future, HttpEntity](_, (_, _) => Future.successful(()))).toList ++
        additionalInterceptors ++
        List(new DecodeFailureInterceptor[Future, HttpEntity](decodeFailureHandler))
    )

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
