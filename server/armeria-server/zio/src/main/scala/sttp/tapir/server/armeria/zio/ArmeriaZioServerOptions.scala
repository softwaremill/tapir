package sttp.tapir.server.armeria.zio

import _root_.zio.{RIO, Task, URIO, ZIO}
import org.slf4j.{Logger, LoggerFactory}
import sttp.tapir.TapirFile
import sttp.tapir.server.armeria.ArmeriaServerOptions
import sttp.tapir.server.interceptor.log.{DefaultServerLog, ServerLog}
import sttp.tapir.server.interceptor.{CustomInterceptors, Interceptor}

final case class ArmeriaZioServerOptions[F[_]](
    createFile: () => F[TapirFile],
    deleteFile: TapirFile => F[Unit],
    interceptors: List[Interceptor[F]]
) extends ArmeriaServerOptions[F] {
  def prependInterceptor(i: Interceptor[F]): ArmeriaZioServerOptions[F] =
    copy(interceptors = i :: interceptors)
  def appendInterceptor(i: Interceptor[F]): ArmeriaZioServerOptions[F] =
    copy(interceptors = interceptors :+ i)
}

object ArmeriaZioServerOptions {

  /** Allows customising the interceptors used by the server interpreter. */
  def customInterceptors[R]: CustomInterceptors[RIO[R, *], ArmeriaZioServerOptions[RIO[R, *]]] =
    CustomInterceptors(
      createOptions = (ci: CustomInterceptors[RIO[R, *], ArmeriaZioServerOptions[RIO[R, *]]]) => {
        ArmeriaZioServerOptions(
          () => ZIO.fromFuture(_ => ArmeriaServerOptions.defaultCreateFile()),
          file => ZIO.fromFuture(_ => ArmeriaServerOptions.defaultDeleteFile(file)),
          ci.interceptors
        )
      }
    ).serverLog(defaultServerLog[R])

  private val logger: Logger = LoggerFactory.getLogger(this.getClass.getPackage.getName)

  implicit def default[R]: ArmeriaZioServerOptions[RIO[R, *]] = customInterceptors.options

  def defaultServerLog[R]: ServerLog[RIO[R, *]] = DefaultServerLog(
    doLogWhenHandled = debugLog[R],
    doLogAllDecodeFailures = debugLog[R],
    doLogExceptions = (msg: String, ex: Throwable) => URIO.succeed { logger.warn(msg, ex) },
    noLog = URIO.unit
  )

  private def debugLog[R](msg: String, exOpt: Option[Throwable]): RIO[R, Unit] =
    URIO.succeed(exOpt match {
      case None     => logger.debug(msg)
      case Some(ex) => logger.debug(msg, ex)
    })
}
