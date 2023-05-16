package sttp.tapir.server.finatra.cats

import cats.effect.Async
import cats.effect.std.Dispatcher
import com.twitter.util.Future
import com.twitter.util.logging.Logging
import sttp.tapir.TapirFile
import sttp.tapir.server.finatra.FinatraServerOptions
import sttp.tapir.server.interceptor.log.DefaultServerLog
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
    def finatraCatsServerLog(finatraServerLog: DefaultServerLog[Future]): DefaultServerLog[F] = DefaultServerLog[F](
      doLogWhenReceived = m => fromFuture(finatraServerLog.doLogWhenReceived(m)),
      doLogWhenHandled = (m, e) => fromFuture(finatraServerLog.doLogWhenHandled(m, e)),
      doLogAllDecodeFailures = (m, e) => fromFuture(finatraServerLog.doLogAllDecodeFailures(m, e)),
      doLogExceptions = (m, e) => fromFuture(finatraServerLog.doLogExceptions(m, e)),
      noLog = fromFuture(finatraServerLog.noLog)
    )

    CustomiseInterceptors(
      createOptions = (ci: CustomiseInterceptors[F, FinatraCatsServerOptions[F]]) =>
        FinatraCatsServerOptions[F](
          dispatcher,
          bs => fromFuture(FinatraServerOptions.defaultCreateFile(FinatraServerOptions.futurePool)(bs)),
          file => fromFuture(FinatraServerOptions.defaultDeleteFile(FinatraServerOptions.futurePool)(file)),
          ci.interceptors
        )
    ).serverLog(finatraCatsServerLog(FinatraServerOptions.defaultServerLog)).rejectHandler(None)
  }

  def default[F[_]: Async](dispatcher: Dispatcher[F]): FinatraCatsServerOptions[F] = customiseInterceptors(dispatcher).options
}
