package sttp.tapir.server.finatra.cats

import cats.effect.std.Dispatcher
import com.twitter.util.Future
import com.twitter.util.logging.Logging
import sttp.tapir.TapirFile
import sttp.tapir.server.finatra.{FinatraContent, FinatraServerOptions}
import sttp.tapir.server.interceptor.log.{ServerLog, ServerLogInterceptor}
import sttp.tapir.server.interceptor.{CustomInterceptors, Interceptor}

case class FinatraCatsServerOptions[F[_]](
    dispatcher: Dispatcher[F],
    createFile: Array[Byte] => Future[TapirFile],
    deleteFile: TapirFile => Future[Unit],
    interceptors: List[Interceptor[Future, FinatraContent]]
)

object FinatraCatsServerOptions extends Logging {

  /** Allows customising the interceptors used by the server interpreter. */
  def customInterceptors[F[_]](dispatcher: Dispatcher[F]): CustomInterceptors[Future, FinatraContent, Unit, FinatraCatsServerOptions[F]] =
    CustomInterceptors(
      createLogInterceptor =
        (sl: ServerLog[Unit]) => new ServerLogInterceptor[Unit, Future, FinatraContent](sl, (_: Unit, _) => Future.Done),
      createOptions = (ci: CustomInterceptors[Future, FinatraContent, Unit, FinatraCatsServerOptions[F]]) =>
        FinatraCatsServerOptions[F](
          dispatcher,
          FinatraServerOptions.defaultCreateFile(FinatraServerOptions.futurePool),
          FinatraServerOptions.defaultDeleteFile(FinatraServerOptions.futurePool),
          ci.interceptors
        )
    ).serverLog(FinatraServerOptions.defaultServerLog)

  def default[F[_]](dispatcher: Dispatcher[F]): FinatraCatsServerOptions[F] = customInterceptors(dispatcher).options
}
