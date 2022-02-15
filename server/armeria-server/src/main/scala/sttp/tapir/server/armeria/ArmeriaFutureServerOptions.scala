package sttp.tapir.server.armeria

import scala.concurrent.Future
import sttp.tapir.TapirFile
import sttp.tapir.server.interceptor.{CustomInterceptors, Interceptor}

final case class ArmeriaFutureServerOptions(
    createFile: () => Future[TapirFile],
    deleteFile: TapirFile => Future[Unit],
    interceptors: List[Interceptor[Future]]
) extends ArmeriaServerOptions[Future] {
  def prependInterceptor(i: Interceptor[Future]): ArmeriaFutureServerOptions = copy(interceptors = i :: interceptors)

  def appendInterceptor(i: Interceptor[Future]): ArmeriaFutureServerOptions = copy(interceptors = interceptors :+ i)
}

object ArmeriaFutureServerOptions {

  /** Allows customising the interceptors used by the server interpreter. */
  def customInterceptors: CustomInterceptors[Future, ArmeriaFutureServerOptions] =
    CustomInterceptors(
      createOptions = (ci: CustomInterceptors[Future, ArmeriaFutureServerOptions]) =>
        ArmeriaFutureServerOptions(
          ArmeriaServerOptions.defaultCreateFile,
          ArmeriaServerOptions.defaultDeleteFile,
          ci.interceptors
        )
    ).serverLog(ArmeriaServerOptions.defaultServerLog)

  val default: ArmeriaFutureServerOptions = customInterceptors.options

}
