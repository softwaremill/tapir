package sttp.tapir.server.netty.cats

import cats.effect.std.Dispatcher
import cats.effect.{Async, Sync}
import com.typesafe.scalalogging.Logger
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interceptor.log.DefaultServerLog
import sttp.tapir.server.interceptor.{CustomiseInterceptors, Interceptor}
import sttp.tapir.server.netty.{NettyDefaults, NettyOptions}
import sttp.tapir.{Defaults, TapirFile}

import java.net.{InetSocketAddress, SocketAddress}

case class NettyCatsServerOptions[F[_], SA <: SocketAddress](
    interceptors: List[Interceptor[F]],
    createFile: ServerRequest => F[TapirFile],
    deleteFile: TapirFile => F[Unit],
    dispatcher: Dispatcher[F],
    nettyOptions: NettyOptions[SA]
) {
  def prependInterceptor(i: Interceptor[F]): NettyCatsServerOptions[F, SA] = copy(interceptors = i :: interceptors)
  def appendInterceptor(i: Interceptor[F]): NettyCatsServerOptions[F, SA] = copy(interceptors = interceptors :+ i)
  def nettyOptions[SA2 <: SocketAddress](o: NettyOptions[SA2]): NettyCatsServerOptions[F, SA2] = copy(nettyOptions = o)
}

object NettyCatsServerOptions {

  /** Default options, using TCP sockets (the most common case). This can be later customised using
    * [[NettyCatsServerOptions#nettyOptions()]].
    */
  def default[F[_]: Async](dispatcher: Dispatcher[F]): NettyCatsServerOptions[F, InetSocketAddress] =
    customiseInterceptors(dispatcher).options

  private def default[F[_]: Async](
      interceptors: List[Interceptor[F]],
      dispatcher: Dispatcher[F]
  ): NettyCatsServerOptions[F, InetSocketAddress] =
    NettyCatsServerOptions(
      interceptors,
      _ => Sync[F].delay(Defaults.createTempFile()),
      file => Sync[F].delay(Defaults.deleteFile()(file)),
      dispatcher,
      NettyOptions.default
    )

  def customiseInterceptors[F[_]: Async](
      dispatcher: Dispatcher[F]
  ): CustomiseInterceptors[F, NettyCatsServerOptions[F, InetSocketAddress]] =
    CustomiseInterceptors(
      createOptions = (ci: CustomiseInterceptors[F, NettyCatsServerOptions[F, InetSocketAddress]]) => default(ci.interceptors, dispatcher)
    ).serverLog(defaultServerLog)

  private val log = Logger[NettyCatsServerInterpreter[cats.Id]]

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
