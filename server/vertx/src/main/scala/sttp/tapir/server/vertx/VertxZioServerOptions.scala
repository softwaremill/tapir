package sttp.tapir.server.vertx

import io.vertx.core.logging.{Logger, LoggerFactory}
import sttp.tapir.{Defaults, TapirFile}
import sttp.tapir.server.interceptor.log.{DefaultServerLog, ServerLog, ServerLogInterceptor}
import sttp.tapir.server.interceptor.{CustomInterceptors, Interceptor}
import sttp.tapir.server.vertx.VertxZioServerInterpreter.monadError
import zio.{RIO, Task, URIO}

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
  def customInterceptors[R]: CustomInterceptors[RIO[R, *], VertxZioServerOptions[RIO[R, *]]] =
    CustomInterceptors(
      createOptions = (ci: CustomInterceptors[RIO[R, *], VertxZioServerOptions[RIO[R, *]]]) =>
        VertxZioServerOptions(
          Defaults.createTempFile().getParentFile.getAbsoluteFile,
          file => Task[Unit](Defaults.deleteFile()(file)),
          maxQueueSizeForReadStream = 16,
          ci.interceptors
        )
    ).serverLog(Log.defaultServerLog[R](LoggerFactory.getLogger("tapir-vertx")))

  implicit def default[R]: VertxZioServerOptions[RIO[R, *]] = customInterceptors.options

  object Log {
    def defaultServerLog[R](log: Logger): ServerLog[RIO[R, *]] = {
      DefaultServerLog(
        doLogWhenHandled = debugLog(log),
        doLogAllDecodeFailures = infoLog(log),
        doLogExceptions = (msg: String, ex: Throwable) => URIO.succeed { log.error(msg, ex) }
      )
    }

    private def debugLog[R](log: Logger)(msg: String, exOpt: Option[Throwable]): RIO[R, Unit] = URIO.succeed {
      VertxServerOptions.debugLog(log)(msg, exOpt)
    }

    private def infoLog[R](log: Logger)(msg: String, exOpt: Option[Throwable]): RIO[R, Unit] = URIO.succeed {
      VertxServerOptions.infoLog(log)(msg, exOpt)
    }
  }
}
