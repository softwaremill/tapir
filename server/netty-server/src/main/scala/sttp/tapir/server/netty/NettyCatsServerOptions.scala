package sttp.tapir.server.netty

import cats.effect.std.Dispatcher
import cats.effect.{Async, Sync}
import com.typesafe.scalalogging.Logger
import sttp.tapir.{Defaults, TapirFile}
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interceptor.log.{DefaultServerLog, ServerLog, ServerLogInterceptor}
import sttp.tapir.server.interceptor.{CustomInterceptors, Interceptor}

import java.net.{InetSocketAddress, SocketAddress}

case class NettyCatsServerOptions[F[_], S <: SocketAddress](
    interceptors: List[Interceptor[F]],
    createFile: ServerRequest => F[TapirFile],
    deleteFile: TapirFile => F[Unit],
    dispatcher: Dispatcher[F],
    nettyOptions: NettyOptions[S]
) {
  def prependInterceptor(i: Interceptor[F]): NettyCatsServerOptions[F, S] = copy(interceptors = i :: interceptors)
  def appendInterceptor(i: Interceptor[F]): NettyCatsServerOptions[F, S] = copy(interceptors = interceptors :+ i)
  def nettyOptions[NEW_S <: SocketAddress](o: NettyOptions[NEW_S]): NettyCatsServerOptions[F, NEW_S] = copy(nettyOptions = o)
}

object NettyCatsServerOptions {
  def default[F[_]: Async](dispatcher: Dispatcher[F]): NettyCatsServerOptions[F, InetSocketAddress] = customInterceptors(dispatcher).options

  def default[F[_]: Async](interceptors: List[Interceptor[F]], dispatcher: Dispatcher[F]): NettyCatsServerOptions[F, InetSocketAddress] =
    NettyCatsServerOptions(
      interceptors,
      _ => Sync[F].delay(Defaults.createTempFile()),
      file => Sync[F].delay(Defaults.deleteFile()(file)),
      dispatcher,
      NettyOptionsBuilder.default.build
    )

  def customInterceptors[F[_]: Async](
      dispatcher: Dispatcher[F]
  ): CustomInterceptors[F, Logger => F[Unit], NettyCatsServerOptions[F, InetSocketAddress]] =
    CustomInterceptors(
      createLogInterceptor =
        (sl: ServerLog[Logger => F[Unit]]) => new ServerLogInterceptor[Logger => F[Unit], F](sl, (_, _) => Sync[F].unit),
      createOptions =
        (ci: CustomInterceptors[F, Logger => F[Unit], NettyCatsServerOptions[F, InetSocketAddress]]) => default(ci.interceptors, dispatcher)
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
