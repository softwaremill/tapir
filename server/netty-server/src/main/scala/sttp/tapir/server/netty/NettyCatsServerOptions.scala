package sttp.tapir.server.netty

import cats.effect.std.Dispatcher
import cats.effect.{Async, Sync}
import com.typesafe.scalalogging.Logger
import sttp.monad.MonadError
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interceptor.log.{DefaultServerLog, ServerLog, ServerLogInterceptor}
import sttp.tapir.server.interceptor.{CustomInterceptors, Interceptor}
import sttp.tapir.server.netty.internal.CatsUtil.CatsMonadError
import sttp.tapir.{Defaults, TapirFile}

case class NettyCatsServerOptions[F[_]](
    host: String,
    port: Int,
    interceptors: List[Interceptor[F]],
    createFile: ServerRequest => F[TapirFile],
    deleteFile: TapirFile => F[Unit],
    dispatcher: Dispatcher[F],
    nettyOptions: NettyOptions
) {
  def host(s: String): NettyCatsServerOptions[F] = copy(host = s)
  def port(p: Int): NettyCatsServerOptions[F] = copy(port = p)
  def randomPort: NettyCatsServerOptions[F] = port(0)
  def prependInterceptor(i: Interceptor[F]): NettyCatsServerOptions[F] = copy(interceptors = i :: interceptors)
  def appendInterceptor(i: Interceptor[F]): NettyCatsServerOptions[F] = copy(interceptors = interceptors :+ i)
  def nettyOptions(o: NettyOptions): NettyCatsServerOptions[F] = copy(nettyOptions = o)
}

object NettyCatsServerOptions {
  def default[F[_]: Async](dispatcher: Dispatcher[F]): NettyCatsServerOptions[F] = customInterceptors(dispatcher).options

  def default[F[_]: Async](interceptors: List[Interceptor[F]], dispatcher: Dispatcher[F]): NettyCatsServerOptions[F] =
    NettyCatsServerOptions(
      NettyDefaults.DefaultHost,
      NettyDefaults.DefaultPort,
      interceptors,
      _ => Sync[F].delay(Defaults.createTempFile()),
      file => Sync[F].delay(Defaults.deleteFile()(file)),
      dispatcher,
      NettyOptions.default
    )

  def customInterceptors[F[_]: Async](dispatcher: Dispatcher[F]): CustomInterceptors[F, NettyCatsServerOptions[F]] =
    CustomInterceptors(
      createOptions = (ci: CustomInterceptors[F, NettyCatsServerOptions[F]]) => default(ci.interceptors, dispatcher)
    ).serverLog(defaultServerLog)

  def defaultServerLog[F[_]: Async]: ServerLog[F] = {
    implicit val monadError: MonadError[F] = new CatsMonadError[F]

    DefaultServerLog(
      doLogWhenHandled = debugLog[F],
      doLogAllDecodeFailures = debugLog[F],
      doLogExceptions = errorLog[F],
      noLog = Async[F].pure(())
    )
  }

  private def debugLog[F[_]: Async](msg: String, exOpt: Option[Throwable]): F[Unit] = Sync[F].delay {
    val log = Logger[NettyCatsServerInterpreter[F]]
    NettyDefaults.debugLog(log, msg, exOpt)
  }

  private def errorLog[F[_]: Async](msg: String, ex: Throwable): F[Unit] = Sync[F].delay {
    val log = Logger[NettyCatsServerInterpreter[F]]
    log.error(msg, ex)
  }
}
