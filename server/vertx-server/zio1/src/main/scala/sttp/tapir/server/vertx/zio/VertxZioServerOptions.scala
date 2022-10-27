package sttp.tapir.server.vertx.zio

import io.vertx.core.logging.{Logger, LoggerFactory}
import sttp.tapir.server.interceptor.log.{DefaultServerLog, ServerLog}
import sttp.tapir.server.interceptor.{CustomiseInterceptors, Interceptor}
import sttp.tapir.{Defaults, TapirFile}
import _root_.zio.{RIO, URIO}
import _root_.zio.blocking._
import sttp.tapir.server.vertx.VertxServerOptions

final case class VertxZioServerOptions[F[_]](
    uploadDirectory: TapirFile,
    deleteFile: TapirFile => F[Unit],
    maxQueueSizeForReadStream: Int,
    interceptors: List[Interceptor[F]]
) extends VertxServerOptions[F] {
  def prependInterceptor(i: Interceptor[F]): VertxZioServerOptions[F] =
    copy(interceptors = i :: interceptors)
  def appendInterceptor(i: Interceptor[F]): VertxZioServerOptions[F] =
    copy(interceptors = interceptors :+ i)
}

object VertxZioServerOptions {

  /** Allows customising the interceptors used by the server interpreter. */
  def customiseInterceptors[R <: Blocking]: CustomiseInterceptors[RIO[R, *], VertxZioServerOptions[RIO[R, *]]] =
    CustomiseInterceptors(
      createOptions = (ci: CustomiseInterceptors[RIO[R, *], VertxZioServerOptions[RIO[R, *]]]) =>
        VertxZioServerOptions(
          VertxServerOptions.uploadDirectory(),
          file => effectBlocking(Defaults.deleteFile()(file)),
          maxQueueSizeForReadStream = 16,
          ci.interceptors
        )
    ).serverLog(defaultServerLog[R](LoggerFactory.getLogger("tapir-vertx")))

  implicit def default[R <: Blocking]: VertxZioServerOptions[RIO[R, *]] = customiseInterceptors.options

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
