package sttp.tapir.server.vertx.zio

import io.vertx.core.logging.{Logger, LoggerFactory}
import sttp.tapir.server.interceptor.log.{DefaultServerLog, ServerLog}
import sttp.tapir.server.interceptor.{CustomiseInterceptors, Interceptor}
import sttp.tapir.{Defaults, TapirFile}
import _root_.zio.{RIO, URIO}
import _root_.zio.blocking._
import sttp.tapir.server.vertx.VertxServerOptions

final case class VertxZioServerOptions[R](
    uploadDirectory: TapirFile,
    deleteFile: TapirFile => RIO[R, Unit],
    maxQueueSizeForReadStream: Int,
    interceptors: List[Interceptor[RIO[R, *]]]
) extends VertxServerOptions[RIO[R, *]] {
  def prependInterceptor(i: Interceptor[RIO[R, *]]): VertxZioServerOptions[R] =
    copy(interceptors = i :: interceptors)
  def appendInterceptor(i: Interceptor[RIO[R, *]]): VertxZioServerOptions[R] =
    copy(interceptors = interceptors :+ i)

  def widen[R2 <: R]: VertxZioServerOptions[R2] = this.asInstanceOf[VertxZioServerOptions[R2]]
}

object VertxZioServerOptions {

  /** Allows customising the interceptors used by the server interpreter. */
  def customiseInterceptors[R <: Blocking]: CustomiseInterceptors[RIO[R, *], VertxZioServerOptions[R]] =
    CustomiseInterceptors(
      createOptions = (ci: CustomiseInterceptors[RIO[R, *], VertxZioServerOptions[R]]) =>
        VertxZioServerOptions(
          VertxServerOptions.uploadDirectory(),
          file => effectBlocking(Defaults.deleteFile()(file)),
          maxQueueSizeForReadStream = 16,
          ci.interceptors
        )
    ).serverLog(defaultServerLog[R](LoggerFactory.getLogger("tapir-vertx")))

  implicit def default[R <: Blocking]: VertxZioServerOptions[R] = customiseInterceptors.options

  def defaultServerLog[R](log: Logger): DefaultServerLog[RIO[R, *]] = {
    DefaultServerLog(
      doLogWhenReceived = debugLog(log)(_, None),
      doLogWhenHandled = debugLog(log),
      doLogAllDecodeFailures = infoLog(log),
      doLogExceptions = (msg: String, ex: Throwable) => URIO.succeed { log.error(msg, ex) },
      noLog = URIO.unit
    )
  }

  private def debugLog[R](log: Logger)(msg: String, exOpt: Option[Throwable]): RIO[R, Unit] = URIO.succeed {
    VertxServerOptions.debugLog(log)(msg, exOpt)
  }

  private def infoLog[R](log: Logger)(msg: String, exOpt: Option[Throwable]): RIO[R, Unit] = URIO.succeed {
    VertxServerOptions.infoLog(log)(msg, exOpt)
  }
}
