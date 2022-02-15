package sttp.tapir.server.armeria.cats

import cats.effect.std.Dispatcher
import cats.effect.{Async, Sync}
import com.linecorp.armeria.server.ServiceRequestContext
import org.slf4j.{Logger, LoggerFactory}
import scala.concurrent.Future
import sttp.tapir.TapirFile
import sttp.tapir.server.armeria.ArmeriaServerOptions
import sttp.tapir.server.interceptor.log.{DefaultServerLog, ServerLog}
import sttp.tapir.server.interceptor.{CustomInterceptors, Interceptor}

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
  def customInterceptors[F[_]](dispatcher: Dispatcher[F])(implicit F: Async[F]): CustomInterceptors[F, ArmeriaCatsServerOptions[F]] = {
    CustomInterceptors(
      createOptions = (ci: CustomInterceptors[F, ArmeriaCatsServerOptions[F]]) => {
        ArmeriaCatsServerOptions(
          dispatcher,
          () => F.fromFuture(F.pure(ArmeriaServerOptions.defaultCreateFile())),
          file => F.fromFuture(F.pure(ArmeriaServerOptions.defaultDeleteFile(file))),
          ci.interceptors
        )
      }
    ).serverLog(defaultServerLog)
  }

  private val logger: Logger = LoggerFactory.getLogger(this.getClass.getPackage.getName)

  def defaultServerLog[F[_]: Async]: ServerLog[F] = DefaultServerLog[F](
    doLogWhenHandled = debugLog[F],
    doLogAllDecodeFailures = debugLog[F],
    doLogExceptions = (msg: String, ex: Throwable) => Sync[F].delay(logger.warn(msg, ex)),
    noLog = Async[F].pure(())
  )

  def default[F[_]: Async](dispatcher: Dispatcher[F]): ArmeriaCatsServerOptions[F] = customInterceptors(dispatcher).options

  private def debugLog[F[_]: Async](msg: String, exOpt: Option[Throwable]): F[Unit] =
    Sync[F].delay(exOpt match {
      case None     => logger.debug(msg)
      case Some(ex) => logger.debug(msg, ex)
    })
}
