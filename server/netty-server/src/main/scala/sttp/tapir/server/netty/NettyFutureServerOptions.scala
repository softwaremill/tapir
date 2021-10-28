package sttp.tapir.server.netty

import com.typesafe.scalalogging.Logger
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interceptor.log.{DefaultServerLog, ServerLog, ServerLogInterceptor}
import sttp.tapir.server.interceptor.{CustomInterceptors, Interceptor}
import sttp.tapir.{Defaults, TapirFile}

import java.net.{InetSocketAddress, SocketAddress}
import scala.concurrent.{Future, blocking}

case class NettyFutureServerOptions[S <: SocketAddress](
    interceptors: List[Interceptor[Future]],
    createFile: ServerRequest => Future[TapirFile],
    deleteFile: TapirFile => Future[Unit],
    nettyOptions: NettyOptions[S]
) {
  def prependInterceptor(i: Interceptor[Future]): NettyFutureServerOptions[S] = copy(interceptors = i :: interceptors)
  def appendInterceptor(i: Interceptor[Future]): NettyFutureServerOptions[S] = copy(interceptors = interceptors :+ i)
  def nettyOptions[NEW_S <: SocketAddress](o: NettyOptions[NEW_S]): NettyFutureServerOptions[NEW_S] = copy(nettyOptions = o)
}

object NettyFutureServerOptions {
  type DefaultOptions = NettyFutureServerOptions[InetSocketAddress]

  val default: DefaultOptions = customInterceptors.options

  def default(interceptors: List[Interceptor[Future]]): DefaultOptions = NettyFutureServerOptions(
    interceptors,
    _ => {
      import scala.concurrent.ExecutionContext.Implicits.global
      Future(blocking(Defaults.createTempFile()))
    },
    file => {
      import scala.concurrent.ExecutionContext.Implicits.global
      Future(blocking(Defaults.deleteFile()(file)))
    },
    NettyOptionsBuilder.default.build
  )

  def customInterceptors: CustomInterceptors[Future, Logger => Future[Unit], DefaultOptions] = {
    CustomInterceptors(
      createLogInterceptor =
        (sl: ServerLog[Logger => Future[Unit]]) => new ServerLogInterceptor[Logger => Future[Unit], Future](sl, (_, _) => Future.unit),
      createOptions = (ci: CustomInterceptors[Future, Logger => Future[Unit], DefaultOptions]) => default(ci.interceptors)
    ).serverLog(defaultServerLog)
  }

  lazy val defaultServerLog: ServerLog[Logger => Future[Unit]] = DefaultServerLog(
    doLogWhenHandled = debugLog,
    doLogAllDecodeFailures = debugLog,
    doLogExceptions = (msg: String, ex: Throwable) => log => Future.successful(log.error(msg, ex)),
    noLog = _ => Future.unit
  )

  private def debugLog(msg: String, exOpt: Option[Throwable]): Logger => Future[Unit] = log =>
    Future.successful(NettyDefaults.debugLog(log, msg, exOpt))
}
