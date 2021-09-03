package sttp.tapir.server.http4s

import cats.Applicative
import cats.effect.Sync
import sttp.tapir.Defaults
import sttp.tapir.internal.TapirFile
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
  def customInterceptors[F[_], G[_]: Sync]: CustomInterceptors[G, G[Unit], Http4sServerOptions[F, G]] = {
    CustomInterceptors(
      createLogInterceptor = Log.serverLogInterceptor[F, G],
      createOptions = (ci: CustomInterceptors[G, G[Unit], Http4sServerOptions[F, G]]) =>
        Http4sServerOptions[F, G](defaultCreateFile[G], defaultDeleteFile[G], 8192, ci.interceptors)
    ).serverLog(Log.defaultServerLog[G])
  }

  def defaultCreateFile[F[_]](implicit sync: Sync[F]): ServerRequest => F[TapirFile] = _ => sync.blocking(Defaults.createTempFile())

  def defaultDeleteFile[F[_]](implicit sync: Sync[F]): TapirFile => F[Unit] = file => sync.blocking(Defaults.deleteFile()(file))

  object Log {
    def defaultServerLog[F[_]: Sync]: DefaultServerLog[F[Unit]] =
      DefaultServerLog[F[Unit]](
        doLogWhenHandled = debugLog[F],
        doLogAllDecodeFailures = debugLog[F],
        doLogExceptions = (msg: String, ex: Throwable) => Sync[F].delay(Http4sServerToHttpInterpreter.log.error(ex)(msg)),
        noLog = Applicative[F].unit
      )

    def serverLogInterceptor[F[_], G[_]](serverLog: ServerLog[G[Unit]]): ServerLogInterceptor[G[Unit], G] =
      new ServerLogInterceptor[G[Unit], G](serverLog, (f, _) => f)

    private def debugLog[F[_]: Sync](msg: String, exOpt: Option[Throwable]): F[Unit] =
      exOpt match {
        case None     => Sync[F].delay(Http4sServerToHttpInterpreter.log.debug(msg))
        case Some(ex) => Sync[F].delay(Http4sServerToHttpInterpreter.log.debug(ex)(msg))
      }
  }

  def default[F[_], G[_]: Sync]: Http4sServerOptions[F, G] = customInterceptors.options
}
