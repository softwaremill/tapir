package sttp.tapir.server.http4s

import cats.effect.Sync
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interceptor.log.DefaultServerLog
import sttp.tapir.server.interceptor.{CustomiseInterceptors, Interceptor}
import sttp.tapir.{Defaults, TapirFile}

case class Http4sServerOptions[F[_]](
    createFile: ServerRequest => F[TapirFile],
    deleteFile: TapirFile => F[Unit],
    ioChunkSize: Int,
    interceptors: List[Interceptor[F]]
) {
  def prependInterceptor(i: Interceptor[F]): Http4sServerOptions[F] =
    copy(interceptors = i :: interceptors)
  def appendInterceptor(i: Interceptor[F]): Http4sServerOptions[F] =
    copy(interceptors = interceptors :+ i)
}

object Http4sServerOptions {

  /** Allows customising the interceptors used by the server interpreter. */
  def customiseInterceptors[F[_]: Sync]: CustomiseInterceptors[F, Http4sServerOptions[F]] = {
    CustomiseInterceptors(
      createOptions = (ci: CustomiseInterceptors[F, Http4sServerOptions[F]]) =>
        Http4sServerOptions[F](defaultCreateFile[F], defaultDeleteFile[F], 8192, ci.interceptors)
    ).serverLog(defaultServerLog)
  }

  def defaultCreateFile[F[_]](implicit sync: Sync[F]): ServerRequest => F[TapirFile] = _ => sync.blocking(Defaults.createTempFile())

  def defaultDeleteFile[F[_]](implicit sync: Sync[F]): TapirFile => F[Unit] = file => sync.blocking(Defaults.deleteFile()(file))

  def defaultServerLog[F[_]: Sync]: DefaultServerLog[F] = {
    DefaultServerLog(
      doLogWhenReceived = debugLog(_, None),
      doLogWhenHandled = debugLog[F],
      doLogAllDecodeFailures = debugLog[F],
      doLogExceptions = (msg: String, ex: Throwable) => Sync[F].delay(Http4sServerInterpreter.log.error(ex)(msg)),
      noLog = Sync[F].pure(())
    )
  }

  private def debugLog[F[_]](msg: String, exOpt: Option[Throwable])(implicit sync: Sync[F]): F[Unit] =
    exOpt match {
      case None     => Sync[F].delay(Http4sServerInterpreter.log.debug(msg))
      case Some(ex) => Sync[F].delay(Http4sServerInterpreter.log.debug(ex)(msg))
    }

  def default[F[_]: Sync]: Http4sServerOptions[F] = customiseInterceptors[F].options
}
