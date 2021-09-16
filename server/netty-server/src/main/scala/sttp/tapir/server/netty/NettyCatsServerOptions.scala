package sttp.tapir.server.netty

import cats.effect.std.Dispatcher
import cats.effect.{Async, Sync}
import com.typesafe.scalalogging.Logger
import sttp.tapir.{Defaults, TapirFile}
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interceptor.log.{DefaultServerLog, ServerLog, ServerLogInterceptor}
import sttp.tapir.server.interceptor.{CustomInterceptors, Interceptor}

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

  def customInterceptors[F[_]: Async](dispatcher: Dispatcher[F]): CustomInterceptors[F, Logger => F[Unit], NettyCatsServerOptions[F]] =
    CustomInterceptors(
      createLogInterceptor =
        (sl: ServerLog[Logger => F[Unit]]) => new ServerLogInterceptor[Logger => F[Unit], F](sl, (_, _) => Sync[F].unit),
      createOptions = (ci: CustomInterceptors[F, Logger => F[Unit], NettyCatsServerOptions[F]]) => default(ci.interceptors, dispatcher)
    ).serverLog(defaultServerLog)

  def defaultServerLog[F[_]: Async]: ServerLog[Logger => F[Unit]] = DefaultServerLog(
    doLogWhenHandled = debugLog[F],
    doLogAllDecodeFailures = debugLog[F],
    doLogExceptions = (msg: String, ex: Throwable) => log => Sync[F].delay(log.error(msg, ex)),
    noLog = _ => Sync[F].unit
  )

  private def debugLog[F[_]: Async](msg: String, exOpt: Option[Throwable]): Logger => F[Unit] = log =>
    Sync[F].delay(NettyDefaults.debugLog(log, msg, exOpt))
}
