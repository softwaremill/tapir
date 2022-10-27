package sttp.tapir.server.armeria.zio

import _root_.zio.{RIO, Task, URIO}
import com.linecorp.armeria.common.CommonPools
import org.slf4j.{Logger, LoggerFactory}
import sttp.tapir.server.armeria.ArmeriaServerOptions
import sttp.tapir.server.interceptor.log.DefaultServerLog
import sttp.tapir.server.interceptor.{CustomiseInterceptors, Interceptor}
import sttp.tapir.{Defaults, TapirFile}

import scala.util.control.NonFatal

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
  def customiseInterceptors[R]: CustomiseInterceptors[RIO[R, *], ArmeriaZioServerOptions[RIO[R, *]]] =
    CustomiseInterceptors(
      createOptions = (ci: CustomiseInterceptors[RIO[R, *], ArmeriaZioServerOptions[RIO[R, *]]]) => {
        ArmeriaZioServerOptions(
          defaultCreateFile,
          defaultDeleteFile,
          ci.interceptors
        )
      }
    ).serverLog(defaultServerLog[R])

  private val logger: Logger = LoggerFactory.getLogger(this.getClass.getPackage.getName)

  implicit def default[R]: ArmeriaZioServerOptions[RIO[R, *]] = customiseInterceptors.options

  def defaultCreateFile[R](): RIO[R, TapirFile] = blocking(Defaults.createTempFile())

  def defaultDeleteFile[R](file: TapirFile): RIO[R, Unit] = blocking(Defaults.deleteFile()(file))

  def defaultServerLog[R]: DefaultServerLog[RIO[R, *]] = DefaultServerLog(
    doLogWhenReceived = debugLog(_, None),
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

  private def blocking[R, T](body: => T): RIO[R, T] = {
    Task.effectAsync { cb =>
      CommonPools
        .blockingTaskExecutor()
        .execute(() => {
          try {
            cb(Task.succeed(body))
          } catch {
            case NonFatal(ex) =>
              cb(Task.fail(ex))
          }
        })
    }
  }
}
