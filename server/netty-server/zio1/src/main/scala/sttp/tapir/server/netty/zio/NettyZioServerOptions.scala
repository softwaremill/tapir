package sttp.tapir.server.netty.zio

import com.typesafe.scalalogging.Logger
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interceptor.log.DefaultServerLog
import sttp.tapir.server.interceptor.{CustomiseInterceptors, Interceptor}
import sttp.tapir.server.netty.{NettyDefaults, NettyOptions}
import sttp.tapir.{Defaults, TapirFile}
import zio.{RIO, ZIO}

import java.net.{InetSocketAddress, SocketAddress}

case class NettyZioServerOptions[R, SA <: SocketAddress](
    interceptors: List[Interceptor[RIO[R, *]]],
    createFile: ServerRequest => RIO[R, TapirFile],
    deleteFile: TapirFile => RIO[R, Unit],
    nettyOptions: NettyOptions[SA]
) {
  def prependInterceptor(i: Interceptor[RIO[R, *]]): NettyZioServerOptions[R, SA] = copy(interceptors = i :: interceptors)
  def appendInterceptor(i: Interceptor[RIO[R, *]]): NettyZioServerOptions[R, SA] = copy(interceptors = interceptors :+ i)
  def nettyOptions[SA2 <: SocketAddress](o: NettyOptions[SA2]): NettyZioServerOptions[R, SA2] = copy(nettyOptions = o)
}

object NettyZioServerOptions {

  /** Default options, using TCP sockets (the most common case). This can be later customised using
    * [[NettyZioServerOptions#nettyOptions()]].
    */
  def default[R]: NettyZioServerOptions[R, InetSocketAddress] =
    customiseInterceptors.options

  private def default[R](
      interceptors: List[Interceptor[RIO[R, *]]]
  ): NettyZioServerOptions[R, InetSocketAddress] =
    NettyZioServerOptions(
      interceptors,
      _ => ZIO(Defaults.createTempFile()),
      file => ZIO(Defaults.deleteFile()(file)),
      NettyOptions.default
    )

  def customiseInterceptors[R]: CustomiseInterceptors[RIO[R, *], NettyZioServerOptions[R, InetSocketAddress]] =
    CustomiseInterceptors(
      createOptions = (ci: CustomiseInterceptors[RIO[R, *], NettyZioServerOptions[R, InetSocketAddress]]) => default(ci.interceptors)
    ).serverLog(defaultServerLog[R])

  private val log: Logger = Logger[NettyZioServerInterpreter[Any]]

  def defaultServerLog[R]: DefaultServerLog[RIO[R, *]] = DefaultServerLog(
    doLogWhenReceived = debugLog(_, None),
    doLogWhenHandled = debugLog[R],
    doLogAllDecodeFailures = debugLog[R],
    doLogExceptions = (msg: String, ex: Throwable) => ZIO.succeed { log.warn(msg, ex) },
    noLog = ZIO.unit
  )

  private def debugLog[R](msg: String, exOpt: Option[Throwable]): RIO[R, Unit] =
    ZIO.succeed(NettyDefaults.debugLog(log, msg, exOpt))

}
