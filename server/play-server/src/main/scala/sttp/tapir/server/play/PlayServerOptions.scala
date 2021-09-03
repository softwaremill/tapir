package sttp.tapir.server.play

import akka.stream.Materializer
import play.api.Logger
import play.api.libs.Files.{SingletonTemporaryFileCreator, TemporaryFileCreator}
import play.api.mvc._
import sttp.tapir.Defaults
import sttp.tapir.internal.TapirFile
import sttp.tapir.server.interceptor.decodefailure.DecodeFailureHandler
import sttp.tapir.server.interceptor.log.{DefaultServerLog, ServerLog, ServerLogInterceptor}
import sttp.tapir.server.interceptor.{CustomInterceptors, Interceptor}

import scala.concurrent.{ExecutionContext, Future}

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
  def customInterceptors(implicit
      mat: Materializer,
      ec: ExecutionContext
  ): CustomInterceptors[Future, Unit, PlayServerOptions] =
    CustomInterceptors(
      createLogInterceptor = (sl: ServerLog[Unit]) => new ServerLogInterceptor[Unit, Future](sl, (_, _) => Future.successful(())),
      createOptions = (ci: CustomInterceptors[Future, Unit, PlayServerOptions]) =>
        PlayServerOptions(
          SingletonTemporaryFileCreator,
          defaultDeleteFile,
          DefaultActionBuilder.apply(PlayBodyParsers.apply().anyContent),
          PlayBodyParsers.apply(),
          ci.decodeFailureHandler,
          ci.interceptors
        )
    ).serverLog(defaultServerLog)

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

  def default(implicit mat: Materializer, ec: ExecutionContext): PlayServerOptions = customInterceptors.options

  lazy val logger: Logger = Logger(this.getClass.getPackage.getName)
}
