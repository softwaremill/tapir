package sttp.tapir.server.finatra.cats

import cats.effect.Async
import cats.effect.std.Dispatcher
import com.twitter.util.Future
import com.twitter.util.logging.Logging
import sttp.tapir.TapirFile
import sttp.tapir.server.finatra.FinatraServerOptions
import sttp.tapir.server.interceptor.log.{DefaultServerLog, ServerLog}
import sttp.tapir.server.interceptor.{CustomiseInterceptors, Interceptor}
import sttp.tapir.server.finatra.cats.conversions._

case class FinatraCatsServerOptions[F[_]](
    dispatcher: Dispatcher[F],
    createFile: Array[Byte] => F[TapirFile],
    deleteFile: TapirFile => F[Unit],
    interceptors: List[Interceptor[F]]
)

object FinatraCatsServerOptions extends Logging {

  /** Allows customising the interceptors used by the server interpreter. */
  def customiseInterceptors[F[_]: Async](dispatcher: Dispatcher[F]): CustomiseInterceptors[F, FinatraCatsServerOptions[F]] = {
    def finatraCatsServerLog(finatraServerLog: DefaultServerLog[Future]): ServerLog[F] = DefaultServerLog[F](
      doLogWhenReceived = m => finatraServerLog.doLogWhenReceived(m).asF,
      doLogWhenHandled = (m, e) => finatraServerLog.doLogWhenHandled(m, e).asF,
      doLogAllDecodeFailures = (m, e) => finatraServerLog.doLogAllDecodeFailures(m, e).asF,
      doLogExceptions = (m, e) => finatraServerLog.doLogExceptions(m, e).asF,
      noLog = finatraServerLog.noLog.asF
    )

    CustomiseInterceptors(
      createOptions = (ci: CustomiseInterceptors[F, FinatraCatsServerOptions[F]]) =>
        FinatraCatsServerOptions[F](
          dispatcher,
          FinatraServerOptions.defaultCreateFile(FinatraServerOptions.futurePool)(_).asF,
          FinatraServerOptions.defaultDeleteFile(FinatraServerOptions.futurePool)(_).asF,
          ci.interceptors
        )
    ).serverLog(finatraCatsServerLog(FinatraServerOptions.defaultServerLog)).rejectHandler(None)
  }

  def default[F[_]: Async](dispatcher: Dispatcher[F]): FinatraCatsServerOptions[F] = customiseInterceptors(dispatcher).options
}
