package sttp.tapir.server.play

import akka.stream.Materializer
import play.api.Logger
import play.api.libs.Files.{SingletonTemporaryFileCreator, TemporaryFileCreator}
import play.api.mvc._
import sttp.tapir.{Defaults, TapirFile}
import sttp.tapir.server.interceptor.decodefailure.DecodeFailureHandler
import sttp.tapir.server.interceptor.log.DefaultServerLog
import sttp.tapir.server.interceptor.{CustomiseInterceptors, Interceptor}

import scala.concurrent.{ExecutionContext, Future, blocking}

case class PlayServerOptions(
    temporaryFileCreator: TemporaryFileCreator,
    deleteFile: TapirFile => Future[Unit],
    defaultActionBuilder: ActionBuilder[Request, AnyContent],
    playBodyParsers: PlayBodyParsers,
    decodeFailureHandler: DecodeFailureHandler,
    interceptors: List[Interceptor[Future]]
) {
  def prependInterceptor(i: Interceptor[Future]): PlayServerOptions = copy(interceptors = i :: interceptors)
  def appendInterceptor(i: Interceptor[Future]): PlayServerOptions = copy(interceptors = interceptors :+ i)
}

object PlayServerOptions {

  /** Allows customising the interceptors used by the server interpreter. */
  def customiseInterceptors(implicit
      mat: Materializer,
      ec: ExecutionContext
  ): CustomiseInterceptors[Future, PlayServerOptions] =
    CustomiseInterceptors(
      createOptions = (ci: CustomiseInterceptors[Future, PlayServerOptions]) =>
        PlayServerOptions(
          SingletonTemporaryFileCreator,
          defaultDeleteFile(_),
          DefaultActionBuilder.apply(PlayBodyParsers.apply().anyContent),
          PlayBodyParsers.apply(),
          ci.decodeFailureHandler,
          ci.interceptors
        )
    ).serverLog(defaultServerLog)

  def defaultDeleteFile(file: TapirFile)(implicit ec: ExecutionContext): Future[Unit] = {
    Future(blocking(Defaults.deleteFile()(file)))
  }

  lazy val defaultServerLog: DefaultServerLog[Future] = {
    DefaultServerLog(
      doLogWhenReceived = debugLog(_, None),
      doLogWhenHandled = debugLog,
      doLogAllDecodeFailures = debugLog,
      doLogExceptions = (msg: String, ex: Throwable) => Future.successful { logger.error(msg, ex) },
      noLog = Future.successful(())
    )
  }

  private def debugLog(msg: String, exOpt: Option[Throwable]): Future[Unit] = Future.successful {
    exOpt match {
      case None     => logger.debug(msg)
      case Some(ex) => logger.debug(s"$msg; exception: {}", ex)
    }
  }

  def default(implicit mat: Materializer, ec: ExecutionContext): PlayServerOptions = customiseInterceptors.options

  lazy val logger: Logger = Logger(this.getClass.getPackage.getName)
}
