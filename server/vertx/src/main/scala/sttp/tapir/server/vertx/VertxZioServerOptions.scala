package sttp.tapir.server.vertx

import io.vertx.core.logging.LoggerFactory
import sttp.tapir.{Defaults, TapirFile}
import sttp.tapir.server.interceptor.log.{ServerLog, ServerLogInterceptor}
import sttp.tapir.server.interceptor.{CustomInterceptors, Interceptor}
import zio.{RIO, Task}

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
      createLogInterceptor = (sl: ServerLog) => new ServerLogInterceptor[RIO[R, *]](sl),
      createOptions = (ci: CustomInterceptors[RIO[R, *], VertxZioServerOptions[RIO[R, *]]]) =>
        VertxZioServerOptions(
          Defaults.createTempFile().getParentFile.getAbsoluteFile,
          file => Task[Unit](Defaults.deleteFile()(file)),
          maxQueueSizeForReadStream = 16,
          ci.interceptors
        )
    ).serverLog(VertxServerOptions.defaultServerLog(LoggerFactory.getLogger("tapir-vertx")))

  implicit def default[R]: VertxZioServerOptions[RIO[R, *]] = customInterceptors.options
}
