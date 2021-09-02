package sttp.tapir.server.vertx

import cats.Applicative
import cats.effect.Sync
import cats.effect.kernel.Async
import cats.effect.std.Dispatcher
import io.vertx.core.logging.LoggerFactory
import sttp.tapir.server.interceptor.log.{ServerLog, ServerLogInterceptor}
import sttp.tapir.server.interceptor.{CustomInterceptors, Interceptor}
import sttp.tapir.{Defaults, TapirFile}

import java.io.File

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
  ): CustomInterceptors[F, Unit, VertxCatsServerOptions[F]] =
    CustomInterceptors(
      createLogInterceptor = (sl: ServerLog[Unit]) => new ServerLogInterceptor[Unit, F](sl, (_, _) => Applicative[F].unit),
      createOptions = (ci: CustomInterceptors[F, Unit, VertxCatsServerOptions[F]]) =>
        VertxCatsServerOptions(
          dispatcher,
          TapirFile.fromFile(File.createTempFile("tapir", null).getParentFile.getAbsoluteFile),
          file => Sync[F].delay(Defaults.deleteFile()(file)),
          maxQueueSizeForReadStream = 16,
          ci.interceptors
        )
    ).serverLog(VertxServerOptions.defaultServerLog(LoggerFactory.getLogger("tapir-vertx")))

  def default[F[_]: Async](dispatcher: Dispatcher[F]): VertxCatsServerOptions[F] = customInterceptors(dispatcher).options
}
