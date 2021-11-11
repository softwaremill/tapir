package sttp.tapir.server.http4s

import cats.effect.Sync
import sttp.monad.MonadError
import sttp.tapir.integ.cats.CatsMonadError
import sttp.tapir.{Defaults, TapirFile}
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interceptor.log.{DefaultServerLog, ServerLog, ServerLogInterceptor}
import sttp.tapir.server.interceptor.{CustomInterceptors, Interceptor}

/** @tparam F
  *   The effect type used for response body streams. Usually the same as `G`.
  * @tparam G
  *   The effect type used for representing arbitrary side-effects, such as creating files or logging. Usually the same as `F`.
  */
case class Http4sServerOptions[F[_], G[_]](
    createFile: ServerRequest => G[TapirFile],
    deleteFile: TapirFile => G[Unit],
    ioChunkSize: Int,
    interceptors: List[Interceptor[G]]
) {
  def prependInterceptor(i: Interceptor[G]): Http4sServerOptions[F, G] =
    copy(interceptors = i :: interceptors)
  def appendInterceptor(i: Interceptor[G]): Http4sServerOptions[F, G] =
    copy(interceptors = interceptors :+ i)
}

object Http4sServerOptions {

  /** Allows customising the interceptors used by the server interpreter. */
  def customInterceptors[F[_], G[_]: Sync]: CustomInterceptors[G, Http4sServerOptions[F, G]] = {
    CustomInterceptors(
      createLogInterceptor = Log.serverLogInterceptor[G],
      createOptions = (ci: CustomInterceptors[G, Http4sServerOptions[F, G]]) =>
        Http4sServerOptions[F, G](defaultCreateFile[G], defaultDeleteFile[G], 8192, ci.interceptors)
    ).serverLog(Log.defaultServerLog)
  }

  def defaultCreateFile[F[_]](implicit sync: Sync[F]): ServerRequest => F[TapirFile] = _ => sync.blocking(Defaults.createTempFile())

  def defaultDeleteFile[F[_]](implicit sync: Sync[F]): TapirFile => F[Unit] = file => sync.blocking(Defaults.deleteFile()(file))

  object Log {
    def defaultServerLog[G[_]: Sync]: DefaultServerLog[G] = {
      implicit val monadError: MonadError[G] = new CatsMonadError[G]

      DefaultServerLog(
        doLogWhenHandled = debugLog[G],
        doLogAllDecodeFailures = debugLog[G],
        doLogExceptions = (msg: String, ex: Throwable) => Sync[G].delay(Http4sServerToHttpInterpreter.log.error(ex)(msg))
      )
    }

    def serverLogInterceptor[G[_]: Sync](serverLog: ServerLog[G]): ServerLogInterceptor[G] =
      new ServerLogInterceptor[G](serverLog)

    private def debugLog[G[_]](msg: String, exOpt: Option[Throwable])(implicit sync: Sync[G]): G[Unit] =
      exOpt match {
        case None     => Sync[G].delay(Http4sServerToHttpInterpreter.log.debug(msg))
        case Some(ex) => Sync[G].delay(Http4sServerToHttpInterpreter.log.debug(ex)(msg))
      }
  }

  def default[F[_], G[_]: Sync]: Http4sServerOptions[F, G] = customInterceptors[F, G].options
}
