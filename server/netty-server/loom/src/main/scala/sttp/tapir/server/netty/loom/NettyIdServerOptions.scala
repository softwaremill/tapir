package sttp.tapir.server.netty.loom

import com.typesafe.scalalogging.Logger
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interceptor.log.{DefaultServerLog, ServerLog}
import sttp.tapir.server.netty.internal.NettyDefaults
import sttp.tapir.server.interceptor.{CustomiseInterceptors, Interceptor}
import sttp.tapir.{Defaults, TapirFile}

case class NettyIdServerOptions(
    interceptors: List[Interceptor[Id]],
    createFile: ServerRequest => TapirFile,
    deleteFile: TapirFile => Unit
) {
  def prependInterceptor(i: Interceptor[Id]): NettyIdServerOptions = copy(interceptors = i :: interceptors)
  def appendInterceptor(i: Interceptor[Id]): NettyIdServerOptions = copy(interceptors = interceptors :+ i)
}

object NettyIdServerOptions {

  /** Default options, using TCP sockets (the most common case). This can be later customised using [[NettyIdServerOptions#nettyOptions()]].
    */
  def default: NettyIdServerOptions = customiseInterceptors.options

  private def default(
      interceptors: List[Interceptor[Id]]
  ): NettyIdServerOptions =
    NettyIdServerOptions(
      interceptors,
      _ => Defaults.createTempFile(),
      Defaults.deleteFile()
    )

  /** Customise the interceptors that are being used when exposing endpoints as a server. By default uses TCP sockets (the most common
    * case), but this can be later customised using [[NettyIdServerOptions#nettyOptions()]].
    */
  def customiseInterceptors: CustomiseInterceptors[Id, NettyIdServerOptions] = {
    CustomiseInterceptors(
      createOptions = (ci: CustomiseInterceptors[Id, NettyIdServerOptions]) => default(ci.interceptors)
    ).serverLog(defaultServerLog)
  }

  private val log = Logger[NettyIdServerInterpreter]

  lazy val defaultServerLog: ServerLog[Id] = {
    DefaultServerLog[Id](
      doLogWhenReceived = debugLog(_, None),
      doLogWhenHandled = debugLog,
      doLogAllDecodeFailures = debugLog,
      doLogExceptions = (msg: String, ex: Throwable) => log.error(msg, ex),
      noLog = ()
    )
  }

  private def debugLog(msg: String, exOpt: Option[Throwable]): Unit = NettyDefaults.debugLog(log, msg, exOpt)
}
