package sttp.tapir.server.ziohttp

import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interceptor.log.DefaultServerLog
import sttp.tapir.server.interceptor.{CustomiseInterceptors, Interceptor}
import sttp.tapir.{Defaults, TapirFile}
import zio.http.WebSocketConfig
import zio.{Cause, RIO, Task, ZIO}

case class ZioHttpServerOptions[R](
    createFile: ServerRequest => Task[TapirFile],
    deleteFile: TapirFile => RIO[R, Unit],
    interceptors: List[Interceptor[RIO[R, *]]],
    customWebSocketConfig: ServerRequest => Option[WebSocketConfig]
) {
  def prependInterceptor(i: Interceptor[RIO[R, *]]): ZioHttpServerOptions[R] =
    copy(interceptors = i :: interceptors)
  def appendInterceptor(i: Interceptor[RIO[R, *]]): ZioHttpServerOptions[R] =
    copy(interceptors = interceptors :+ i)
  def withCustomWebSocketConfig(f: ServerRequest => WebSocketConfig): ZioHttpServerOptions[R] =
    copy(customWebSocketConfig = f.andThen(Some(_)))

  def widen[R2 <: R]: ZioHttpServerOptions[R2] = this.asInstanceOf[ZioHttpServerOptions[R2]]
}

object ZioHttpServerOptions {

  /** Allows customising the interceptors used by the server interpreter. */
  def customiseInterceptors[R]: CustomiseInterceptors[RIO[R, *], ZioHttpServerOptions[R]] =
    CustomiseInterceptors(
      createOptions = (ci: CustomiseInterceptors[RIO[R, *], ZioHttpServerOptions[R]]) =>
        ZioHttpServerOptions(
          defaultCreateFile,
          defaultDeleteFile,
          ci.interceptors,
          // Tapir's webSocketBody contains configuration if close frames should be decoded and passed to user code or
          // not; but Tapir's WS-handling code must first receive those close frames at all, hence we request them to
          // be forwarded by zio-http (which is not the default)
          _ => Some(WebSocketConfig.default.forwardCloseFrames(true))
        )
    ).serverLog(defaultServerLog[R])

  def defaultCreateFile: ServerRequest => Task[TapirFile] = _ => ZIO.attempt(Defaults.createTempFile())

  def defaultDeleteFile[R]: TapirFile => Task[Unit] = file => ZIO.attempt(Defaults.deleteFile()(file))

  def defaultServerLog[R]: DefaultServerLog[RIO[R, *]] = DefaultServerLog(
    doLogWhenReceived = debugLog(_, None),
    doLogWhenHandled = debugLog[R],
    doLogAllDecodeFailures = debugLog[R],
    doLogExceptions = (msg: String, ex: Throwable) => ZIO.logErrorCause(msg, Cause.fail(ex)),
    noLog = ZIO.unit
  )

  private def debugLog[R](msg: String, exOpt: Option[Throwable]): RIO[R, Unit] =
    exOpt match {
      case None     => ZIO.logDebug(msg)
      case Some(ex) => ZIO.logDebugCause(msg, Cause.fail(ex))
    }

  def default[R]: ZioHttpServerOptions[R] = customiseInterceptors.options
}
