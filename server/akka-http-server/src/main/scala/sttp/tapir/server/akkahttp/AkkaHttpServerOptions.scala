package sttp.tapir.server.akkahttp

import akka.event.{LoggingAdapter, NoLogging}
import akka.http.scaladsl.server.RequestContext
import sttp.monad.{FutureMonad, MonadError}
import sttp.tapir.model.{ServerRequest, ServerResponse}
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

class AkkaHttpServerLog extends ServerLog[Future] {
  import scala.concurrent.ExecutionContext.Implicits.global
  implicit val monadError: MonadError[Future] = new FutureMonad()

  private val defaultServerLog: LoggingAdapter => DefaultServerLog[Future] = (log: LoggingAdapter) => {
    DefaultServerLog[Future](
      doLogWhenHandled = debugLog(log),
      doLogAllDecodeFailures = debugLog(log),
      doLogExceptions = errorLog(log),
      noLog = Future.successful(())
    )
  }

  private def loggerFrom(request: ServerRequest): LoggingAdapter = request.underlying match {
    case rc: RequestContext => rc.log
    case _                  => NoLogging
  }
  private def loggerFrom(ctx: DecodeFailureContext): LoggingAdapter = loggerFrom(ctx.request)

  private def debugLog(log: LoggingAdapter)(msg: String, exOpt: Option[Throwable]): Future[Unit] = Future.successful {
    exOpt match {
      case None     => log.debug(msg)
      case Some(ex) => log.debug(s"$msg; exception: {}", ex)
    }
  }

  private def errorLog(log: LoggingAdapter)(msg: String, ex: Throwable): Future[Unit] = Future.successful {
    log.error(ex, msg)
  }

  override def decodeFailureNotHandled(ctx: DecodeFailureContext): Future[Unit] =
    defaultServerLog(loggerFrom(ctx)).decodeFailureNotHandled(ctx)

  override def decodeFailureHandled(
      ctx: DecodeFailureContext,
      response: ServerResponse[_]
  ): Future[Unit] = defaultServerLog(loggerFrom(ctx)).decodeFailureHandled(ctx, response)

  override def securityFailureHandled(
      ctx: SecurityFailureContext[Future, _],
      response: ServerResponse[_]
  ): Future[Unit] = defaultServerLog(loggerFrom(ctx.request)).securityFailureHandled(ctx, response)

  override def requestHandled(
      ctx: DecodeSuccessContext[Future, _, _],
      response: ServerResponse[_]
  ): Future[Unit] = defaultServerLog(loggerFrom(ctx.request)).requestHandled(ctx, response)

  override def exception(e: AnyEndpoint, request: ServerRequest, ex: Throwable): Future[Unit] =
    defaultServerLog(loggerFrom(request)).exception(e, request, ex)
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

  val default: AkkaHttpServerOptions = customInterceptors.options
}
