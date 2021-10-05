package sttp.tapir.server.ziohttp

import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interceptor.log.{ServerLog, ServerLogInterceptor}
import sttp.tapir.server.interceptor.{CustomInterceptors, Interceptor}
import sttp.tapir.{Defaults, File}
import zio.{RIO, Task}

case class ZioHttpServerOptions[R](
    createFile: ServerRequest => Task[File],
    deleteFile: File => RIO[R, Unit],
    interceptors: List[Interceptor[RIO[R, *]]]
) {
  def prependInterceptor(i: Interceptor[RIO[R, *]]): ZioHttpServerOptions[R] =
    copy(interceptors = i :: interceptors)
  def appendInterceptor(i: Interceptor[RIO[R, *]]): ZioHttpServerOptions[R] =
    copy(interceptors = interceptors :+ i)
}

object ZioHttpServerOptions {

  /** Allows customising the interceptors used by the server interpreter. */
  def customInterceptors[R]: CustomInterceptors[RIO[R, *], Unit, ZioHttpServerOptions[R]] =
    CustomInterceptors(
      createLogInterceptor = (sl: ServerLog[Unit]) => new ServerLogInterceptor[Unit, RIO[R, *]](sl, (_, _) => RIO.unit),
      createOptions = (ci: CustomInterceptors[RIO[R, *], Unit, ZioHttpServerOptions[R]]) =>
        ZioHttpServerOptions(
          defaultCreateFile,
          defaultDeleteFile,
          ci.interceptors
        )
    )

  def defaultCreateFile: ServerRequest => Task[File] = _ => Task.effect(Defaults.createTempFile())

  def defaultDeleteFile[R]: File => Task[Unit] = file => Task.effect(Defaults.deleteFile()(file))

  def default[R]: ZioHttpServerOptions[R] = customInterceptors.options
}
