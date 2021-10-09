package sttp.tapir.server.netty

import cats.effect.std.Dispatcher
import cats.effect.{Async, Sync}
import com.typesafe.scalalogging.Logger
import sttp.tapir.{Defaults, TapirFile}
import sttp.monad.MonadError
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interceptor.log.{DefaultServerLog, ServerLog, ServerLogInterceptor}
import sttp.tapir.server.interceptor.{CustomiseInterceptors, Interceptor}
import sttp.tapir.server.netty.internal.CatsUtil.CatsMonadError
import sttp.tapir.{Defaults, TapirFile}

import java.net.{InetSocketAddress, SocketAddress}

case class NettyCatsServerOptions[F[_], S <: SocketAddress](
    host: String,
    port: Int,
    interceptors: List[Interceptor[F]],
    createFile: ServerRequest => F[TapirFile],
    deleteFile: TapirFile => F[Unit],
    dispatcher: Dispatcher[F],
    nettyOptions: NettyOptions[S]
) {
  def host(s: String): NettyCatsServerOptions[F, S] = copy(host = s)
  def port(p: Int): NettyCatsServerOptions[F, S] = copy(port = p)
  def randomPort: NettyCatsServerOptions[F, S] = port(0)
  def prependInterceptor(i: Interceptor[F]): NettyCatsServerOptions[F, S] = copy(interceptors = i :: interceptors)
  def appendInterceptor(i: Interceptor[F]): NettyCatsServerOptions[F, S] = copy(interceptors = interceptors :+ i)
  def nettyOptions[NEW_S <: SocketAddress](o: NettyOptions[NEW_S]): NettyCatsServerOptions[F, NEW_S] = copy(nettyOptions = o)
}

object NettyCatsServerOptions {
  def default[F[_]: Async](dispatcher: Dispatcher[F]): NettyCatsServerOptions[F, InetSocketAddress] = customInterceptors(dispatcher).options
//  def default[F[_]: Async](dispatcher: Dispatcher[F]): NettyCatsServerOptions[F] = customiseInterceptors(dispatcher).options

  def default[F[_]: Async](interceptors: List[Interceptor[F]], dispatcher: Dispatcher[F]): NettyCatsServerOptions[F, InetSocketAddress] =
    NettyCatsServerOptions(
      NettyDefaults.DefaultHost,
      NettyDefaults.DefaultPort,
      interceptors,
      _ => Sync[F].delay(Defaults.createTempFile()),
      file => Sync[F].delay(Defaults.deleteFile()(file)),
      dispatcher,
//      NettyOptions.default
      NettyOptionsBuilder.default.build
    )

  def customiseInterceptors[F[_]: Async](dispatcher: Dispatcher[F]): CustomiseInterceptors[F, NettyCatsServerOptions[F]] =
    CustomiseInterceptors(
      createOptions = (ci: CustomiseInterceptors[F, NettyCatsServerOptions[F, InetSocketAddress]]) => default(ci.interceptors, dispatcher)
    ).serverLog(defaultServerLog)

//  CustomInterceptors(
//    createLogInterceptor =
//      (sl: ServerLog[Logger => F[Unit]]) => new ServerLogInterceptor[Logger => F[Unit], F](sl, (_, _) => Sync[F].unit),
//    createOptions =
//      (ci: CustomInterceptors[F, Logger => F[Unit], NettyCatsServerOptions[F, InetSocketAddress]]) => default(ci.interceptors, dispatcher)
//  ).serverLog(defaultServerLog)

  private val log = Logger[NettyCatsServerInterpreter[cats.Id]]

  def defaultServerLog[F[_]: Async]: ServerLog[F] = {
    implicit val monadError: MonadError[F] = new CatsMonadError[F]

    DefaultServerLog(
      doLogWhenReceived = debugLog(_, None),
      doLogWhenHandled = debugLog[F],
      doLogAllDecodeFailures = debugLog[F],
      doLogExceptions = errorLog[F],
      noLog = Async[F].pure(())
    )
  }

  private def debugLog[F[_]: Async](msg: String, exOpt: Option[Throwable]): F[Unit] = Sync[F].delay {
    NettyDefaults.debugLog(log, msg, exOpt)
  }

  private def errorLog[F[_]: Async](msg: String, ex: Throwable): F[Unit] = Sync[F].delay {
    log.error(msg, ex)
  }
}
