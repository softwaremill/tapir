package sttp.tapir.server.netty.cats

import cats.effect.std.Dispatcher
import cats.effect.{Async, Sync}
import org.slf4j.LoggerFactory
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interceptor.log.DefaultServerLog
import sttp.tapir.server.interceptor.reject.DefaultRejectHandler
import sttp.tapir.server.interceptor.{CustomiseInterceptors, Interceptor}
import sttp.tapir.server.netty.NettyServerOptions
import sttp.tapir.server.netty.internal.NettyDefaults
import sttp.tapir.{Defaults, TapirFile}

/** Options configuring the [[NettyCatsServerInterpreter]], which is being used by [[NettyCatsServer]] to interpret tapir's
  * [[sttp.tapir.server.ServerEndpoint]]s so that they can be served using a Netty server. Contains the interceptors stack and functions for
  * file handling.
  */
case class NettyCatsServerOptions[F[_]](
    interceptors: List[Interceptor[F]],
    createFile: ServerRequest => F[TapirFile],
    deleteFile: TapirFile => F[Unit],
    multipartTempDirectory: Option[TapirFile],
    multipartMinSizeForDisk: Option[Long],
    dispatcher: Dispatcher[F]
) extends NettyServerOptions[F] {
  def prependInterceptor(i: Interceptor[F]): NettyCatsServerOptions[F] = copy(interceptors = i :: interceptors)
  def appendInterceptor(i: Interceptor[F]): NettyCatsServerOptions[F] = copy(interceptors = interceptors :+ i)
}

object NettyCatsServerOptions {

  def default[F[_]: Async](dispatcher: Dispatcher[F]): NettyCatsServerOptions[F] =
    customiseInterceptors(dispatcher).options

  private def default[F[_]: Async](
      interceptors: List[Interceptor[F]],
      dispatcher: Dispatcher[F]
  ): NettyCatsServerOptions[F] =
    NettyCatsServerOptions(
      interceptors,
      _ => Sync[F].delay(Defaults.createTempFile()),
      file => Sync[F].delay(Defaults.deleteFile()(file)),
      None,
      None,
      dispatcher
    )

  def customiseInterceptors[F[_]: Async](
      dispatcher: Dispatcher[F]
  ): CustomiseInterceptors[F, NettyCatsServerOptions[F]] =
    CustomiseInterceptors(
      createOptions = (ci: CustomiseInterceptors[F, NettyCatsServerOptions[F]]) => default(ci.interceptors, dispatcher)
    ).serverLog(defaultServerLog).rejectHandler(DefaultRejectHandler.orNotFound[F])

  private val log = LoggerFactory.getLogger(classOf[NettyCatsServerInterpreter[cats.Id]].getName)

  def defaultServerLog[F[_]: Async]: DefaultServerLog[F] = DefaultServerLog(
    doLogWhenReceived = debugLog(_, None),
    doLogWhenHandled = debugLog[F],
    doLogAllDecodeFailures = debugLog[F],
    doLogExceptions = errorLog[F],
    noLog = Async[F].pure(())
  )

  private def debugLog[F[_]: Async](msg: String, exOpt: Option[Throwable]): F[Unit] = Sync[F].delay {
    NettyDefaults.debugLog(log, msg, exOpt)
  }

  private def errorLog[F[_]: Async](msg: String, ex: Throwable): F[Unit] = Sync[F].delay {
    log.error(msg, ex)
  }
}
