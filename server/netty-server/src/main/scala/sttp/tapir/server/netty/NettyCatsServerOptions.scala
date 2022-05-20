package sttp.tapir.server.netty

import cats.effect.std.Dispatcher
import cats.effect.{Async, Sync}
import com.typesafe.scalalogging.Logger
import sttp.monad.MonadError
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interceptor.log.{DefaultServerLog, ServerLog}
import sttp.tapir.server.interceptor.{CustomiseInterceptors, Interceptor}
import sttp.tapir.server.netty.internal.CatsUtil.CatsMonadError
import sttp.tapir.{Defaults, TapirFile}

case class NettyCatsServerOptions[F[_]](
    interceptors: List[Interceptor[F]],
    createFile: ServerRequest => F[TapirFile],
    deleteFile: TapirFile => F[Unit],
    dispatcher: Dispatcher[F],
    nettyOptions: NettyOptions
) {

  def prependInterceptor(i: Interceptor[F]): NettyCatsServerOptions[F] = copy(interceptors = i :: interceptors)
  def appendInterceptor(i: Interceptor[F]): NettyCatsServerOptions[F] = copy(interceptors = interceptors :+ i)
  def nettyOptions(o: NettyOptions): NettyCatsServerOptions[F] = copy(nettyOptions = o)
}

object NettyCatsServerOptions {
  def defaultTcp[F[_]: Async](dispatcher: Dispatcher[F]): NettyCatsServerOptions[F] = customiseInterceptors(
    dispatcher,
    NettyOptionsBuilder.make().tcp().build
  ).options

  def defaultDomainSocket[F[_]: Async](dispatcher: Dispatcher[F]): NettyCatsServerOptions[F] = customiseInterceptors(
    dispatcher,
    NettyOptionsBuilder.make().domainSocket().build
  ).options

  def default[F[_]: Async](
      interceptors: List[Interceptor[F]],
      dispatcher: Dispatcher[F],
      nettyOptions: NettyOptions
  ): NettyCatsServerOptions[F] =
    NettyCatsServerOptions(
      interceptors,
      _ => Sync[F].delay(Defaults.createTempFile()),
      file => Sync[F].delay(Defaults.deleteFile()(file)),
      dispatcher,
      nettyOptions
    )

  def customiseInterceptors[F[_]: Async](
      dispatcher: Dispatcher[F],
      nettyOptions: NettyOptions
  ): CustomiseInterceptors[F, NettyCatsServerOptions[F]] =
    CustomiseInterceptors(
      createOptions = (ci: CustomiseInterceptors[F, NettyCatsServerOptions[F]]) => default(ci.interceptors, dispatcher, nettyOptions)
    ).serverLog(defaultServerLog)

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
