package sttp.tapir.server.vertx

import cats.effect.Sync
import cats.effect.kernel.Async
import cats.effect.std.Dispatcher
import io.vertx.core.logging.{Logger, LoggerFactory}
import sttp.tapir.{Defaults, TapirFile}
import sttp.tapir.server.interceptor.log.{DefaultServerLog, ServerLog, ServerLogInterceptor}
import sttp.tapir.server.interceptor.{CustomInterceptors, Interceptor}
import sttp.tapir.server.vertx.VertxCatsServerInterpreter.monadError

final case class VertxCatsServerOptions[F[_]](
    dispatcher: Dispatcher[F],
    uploadDirectory: TapirFile,
    deleteFile: TapirFile => F[Unit],
    maxQueueSizeForReadStream: Int,
    interceptors: List[Interceptor[F]]
) extends VertxServerOptions[F] {
  def prependInterceptor(i: Interceptor[F]): VertxCatsServerOptions[F] =
    copy(interceptors = i :: interceptors)
  def appendInterceptor(i: Interceptor[F]): VertxCatsServerOptions[F] =
    copy(interceptors = interceptors :+ i)
}

object VertxCatsServerOptions {

  /** Allows customising the interceptors used by the server interpreter. */
  def customInterceptors[F[_]: Async](
      dispatcher: Dispatcher[F]
  ): CustomInterceptors[F, VertxCatsServerOptions[F]] =
    CustomInterceptors(
      createLogInterceptor = (sl: ServerLog[F]) => new ServerLogInterceptor[F](sl),
      createOptions = (ci: CustomInterceptors[F, VertxCatsServerOptions[F]]) =>
        VertxCatsServerOptions(
          dispatcher,
          Defaults.createTempFile().getParentFile.getAbsoluteFile,
          file => Sync[F].delay(Defaults.deleteFile()(file)),
          maxQueueSizeForReadStream = 16,
          ci.interceptors
        )
    ).serverLog(Log.defaultServerLog(LoggerFactory.getLogger("tapir-vertx")))

  def default[F[_]: Async](dispatcher: Dispatcher[F]): VertxCatsServerOptions[F] = customInterceptors(dispatcher).options

  object Log {
    def defaultServerLog[F[_]: Async](log: Logger): ServerLog[F] = {
      DefaultServerLog(
        doLogWhenHandled = debugLog[F](log),
        doLogAllDecodeFailures = infoLog[F](log),
        doLogExceptions = (msg: String, ex: Throwable) => Sync[F].delay { log.error(msg, ex) }
      )(monadError)
    }

    private def debugLog[F[_]: Async](log: Logger)(msg: String, exOpt: Option[Throwable]): F[Unit] = Sync[F].delay {
      VertxServerOptions.debugLog(log)(msg, exOpt)
    }

    private def infoLog[F[_]: Async](log: Logger)(msg: String, exOpt: Option[Throwable]): F[Unit] = Sync[F].delay {
      VertxServerOptions.infoLog(log)(msg, exOpt)
    }
  }
}
