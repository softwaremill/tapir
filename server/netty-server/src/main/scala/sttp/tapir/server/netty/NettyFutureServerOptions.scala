package sttp.tapir.server.netty

import com.typesafe.scalalogging.Logger
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interceptor.log.DefaultServerLog
import sttp.tapir.server.interceptor.{CustomiseInterceptors, Interceptor}
import sttp.tapir.{Defaults, TapirFile}

import java.net.{InetSocketAddress, SocketAddress}
import scala.concurrent.{Future, blocking}

case class NettyFutureServerOptions[SA <: SocketAddress](
    interceptors: List[Interceptor[Future]],
    createFile: ServerRequest => Future[TapirFile],
    deleteFile: TapirFile => Future[Unit],
    nettyOptions: NettyOptions[SA]
) {
  def prependInterceptor(i: Interceptor[Future]): NettyFutureServerOptions[SA] = copy(interceptors = i :: interceptors)
  def appendInterceptor(i: Interceptor[Future]): NettyFutureServerOptions[SA] = copy(interceptors = interceptors :+ i)
  def nettyOptions[SA2 <: SocketAddress](o: NettyOptions[SA2]): NettyFutureServerOptions[SA2] = copy(nettyOptions = o)
}

object NettyFutureServerOptions {

  /** Default options, using TCP sockets (the most common case). This can be later customised using
    * [[NettyFutureServerOptions#nettyOptions()]].
    */
  def default: NettyFutureServerOptions[InetSocketAddress] = customiseInterceptors.options

  private def default[SA <: SocketAddress](
      interceptors: List[Interceptor[Future]],
      nettyOptions: NettyOptions[SA]
  ): NettyFutureServerOptions[SA] =
    NettyFutureServerOptions(
      interceptors,
      _ => {
        import scala.concurrent.ExecutionContext.Implicits.global
        Future(blocking(Defaults.createTempFile()))
      },
      file => {
        import scala.concurrent.ExecutionContext.Implicits.global
        Future(blocking(Defaults.deleteFile()(file)))
      },
      nettyOptions
    )

  /** Customise the interceptors that are being used when exposing endpoints as a server. By default uses TCP sockets (the most common
    * case), but this can be later customised using [[NettyFutureServerOptions#nettyOptions()]].
    */
  def customiseInterceptors: CustomiseInterceptors[Future, NettyFutureServerOptions[InetSocketAddress]] = {
    CustomiseInterceptors(
      createOptions =
        (ci: CustomiseInterceptors[Future, NettyFutureServerOptions[InetSocketAddress]]) => default(ci.interceptors, NettyOptions.default)
    ).serverLog(defaultServerLog)
  }

  private val log = Logger[NettyFutureServerInterpreter]

  lazy val defaultServerLog: DefaultServerLog[Future] = {
    DefaultServerLog(
      doLogWhenReceived = debugLog(_, None),
      doLogWhenHandled = debugLog,
      doLogAllDecodeFailures = debugLog,
      doLogExceptions = (msg: String, ex: Throwable) => Future.successful { log.error(msg, ex) },
      noLog = Future.successful(())
    )
  }

  private def debugLog(msg: String, exOpt: Option[Throwable]): Future[Unit] = Future.successful {
    NettyDefaults.debugLog(log, msg, exOpt)
  }
}
