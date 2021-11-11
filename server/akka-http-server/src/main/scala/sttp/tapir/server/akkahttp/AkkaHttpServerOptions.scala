package sttp.tapir.server.akkahttp

import akka.event.LoggingAdapter
import akka.http.scaladsl.server.RequestContext
import sttp.monad.{FutureMonad, MonadError}
import sttp.tapir.model.{ServerRequest, ServerResponse}
import sttp.tapir.server.interceptor.log.{DefaultServerLog, ServerLog, ServerLogInterceptor}
import sttp.tapir.server.interceptor.{CustomInterceptors, DecodeFailureContext, Interceptor}
import sttp.tapir.{AnyEndpoint, Defaults, TapirFile}

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
      doLogExceptions = errorLog(log)
    )
  }

  private def loggerFrom(request: ServerRequest): LoggingAdapter = request.underlying.asInstanceOf[RequestContext].log
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
      e: AnyEndpoint,
      request: ServerRequest,
      response: ServerResponse[_]
  ): Future[Unit] = defaultServerLog(loggerFrom(request)).securityFailureHandled(e, request, response)

  override def requestHandled(
      e: AnyEndpoint,
      request: ServerRequest,
      response: ServerResponse[_]
  ): Future[Unit] = defaultServerLog(loggerFrom(request)).requestHandled(e, request, response)

  override def exception(e: AnyEndpoint, request: ServerRequest, ex: Throwable): Future[Unit] =
    defaultServerLog(loggerFrom(request)).exception(e, request, ex)
}

object AkkaHttpServerOptions {

  /** Allows customising the interceptors used by the server interpreter. */
  def customInterceptors: CustomInterceptors[Future, AkkaHttpServerOptions] =
    CustomInterceptors(
      createLogInterceptor = Log.serverLogInterceptor,
      createOptions = (ci: CustomInterceptors[Future, AkkaHttpServerOptions]) =>
        AkkaHttpServerOptions(defaultCreateFile, defaultDeleteFile, ci.interceptors)
    ).serverLog(Log.defaultServerLog)

  val defaultCreateFile: ServerRequest => Future[TapirFile] = { _ =>
    import scala.concurrent.ExecutionContext.Implicits.global
    Future(blocking(Defaults.createTempFile()))
  }

  val defaultDeleteFile: TapirFile => Future[Unit] = file => {
    import scala.concurrent.ExecutionContext.Implicits.global
    Future(blocking(Defaults.deleteFile()(file)))
  }

  object Log {
    val defaultServerLog: ServerLog[Future] = new AkkaHttpServerLog

    def serverLogInterceptor(
        serverLog: ServerLog[Future]
    ): ServerLogInterceptor[Future] =
      new ServerLogInterceptor[Future](serverLog)
  }

  val default: AkkaHttpServerOptions = customInterceptors.options
}
