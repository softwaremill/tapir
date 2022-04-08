package sttp.tapir.server.armeria.cats

import cats.effect.std.Dispatcher
import cats.effect.{Async, Sync}
import com.linecorp.armeria.common.CommonPools
import org.slf4j.{Logger, LoggerFactory}
import scala.util.control.NonFatal
import sttp.tapir.server.armeria.ArmeriaServerOptions
import sttp.tapir.server.interceptor.log.{DefaultServerLog, ServerLog}
import sttp.tapir.server.interceptor.{CustomiseInterceptors, Interceptor}
import sttp.tapir.{Defaults, TapirFile}

final case class ArmeriaCatsServerOptions[F[_]](
    dispatcher: Dispatcher[F],
    createFile: () => F[TapirFile],
    deleteFile: TapirFile => F[Unit],
    interceptors: List[Interceptor[F]]
) extends ArmeriaServerOptions[F] {
  def prependInterceptor(i: Interceptor[F]): ArmeriaCatsServerOptions[F] =
    copy(interceptors = i :: interceptors)

  def appendInterceptor(i: Interceptor[F]): ArmeriaCatsServerOptions[F] =
    copy(interceptors = interceptors :+ i)
}

object ArmeriaCatsServerOptions {

  /** Allows customising the interceptors used by the server interpreter. */
  def customiseInterceptors[F[_]](
      dispatcher: Dispatcher[F]
  )(implicit F: Async[F]): CustomiseInterceptors[F, ArmeriaCatsServerOptions[F]] = {
    CustomiseInterceptors(
      createOptions = (ci: CustomiseInterceptors[F, ArmeriaCatsServerOptions[F]]) => {
        ArmeriaCatsServerOptions[F](
          dispatcher,
          () => defaultCreateFile()(F),
          file => defaultDeleteFile(file)(F),
          ci.interceptors
        )
      }
    ).serverLog(defaultServerLog)
  }

  private val logger: Logger = LoggerFactory.getLogger(this.getClass.getPackage.getName)

  def defaultCreateFile[F[_]: Async](): F[TapirFile] = blocking(Defaults.createTempFile())

  def defaultDeleteFile[F[_]: Async](file: TapirFile): F[Unit] = blocking(Defaults.deleteFile()(file))

  def defaultServerLog[F[_]: Async]: ServerLog[F] = DefaultServerLog[F](
    doLogWhenReceived = debugLog(_, None),
    doLogWhenHandled = debugLog[F],
    doLogAllDecodeFailures = debugLog[F],
    doLogExceptions = (msg: String, ex: Throwable) => Sync[F].delay(logger.warn(msg, ex)),
    noLog = Async[F].pure(())
  )

  def default[F[_]: Async](dispatcher: Dispatcher[F]): ArmeriaCatsServerOptions[F] = customiseInterceptors(dispatcher).options

  private def debugLog[F[_]: Async](msg: String, exOpt: Option[Throwable]): F[Unit] =
    Sync[F].delay(exOpt match {
      case None     => logger.debug(msg)
      case Some(ex) => logger.debug(msg, ex)
    })

  private def blocking[F[_], T](body: => T)(implicit F: Async[F]): F[T] = {
    F.async_ { cb =>
      CommonPools
        .blockingTaskExecutor()
        .execute(() => {
          try {
            cb(Right(body))
          } catch {
            case NonFatal(ex) =>
              cb(Left(ex))
          }
        })
    }
  }
}
