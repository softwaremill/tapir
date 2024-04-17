package sttp.tapir.server.netty.loom

import org.slf4j.LoggerFactory
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interceptor.log.{DefaultServerLog, ServerLog}
import sttp.tapir.server.netty.internal.NettyDefaults
import sttp.tapir.server.interceptor.{CustomiseInterceptors, Interceptor}
import sttp.tapir.{Defaults, TapirFile}

case class NettySyncServerOptions(
    interceptors: List[Interceptor[Id]],
    createFile: ServerRequest => TapirFile,
    deleteFile: TapirFile => Unit
) {
  def prependInterceptor(i: Interceptor[Id]): NettySyncServerOptions = copy(interceptors = i :: interceptors)
  def appendInterceptor(i: Interceptor[Id]): NettySyncServerOptions = copy(interceptors = interceptors :+ i)
}

object NettySyncServerOptions {

  /** Default options, using TCP sockets (the most common case). This can be later customised using [[NettySyncServerOptions#nettyOptions()]].
    */
  def default: NettySyncServerOptions = customiseInterceptors.options

  private def default(
      interceptors: List[Interceptor[Id]]
  ): NettySyncServerOptions =
    NettySyncServerOptions(
      interceptors,
      _ => Defaults.createTempFile(),
      Defaults.deleteFile()
    )

  /** Customise the interceptors that are being used when exposing endpoints as a server. By default uses TCP sockets (the most common
    * case), but this can be later customised using [[NettySyncServerOptions#nettyOptions()]].
    */
  def customiseInterceptors: CustomiseInterceptors[Id, NettySyncServerOptions] = {
    CustomiseInterceptors(
      createOptions = (ci: CustomiseInterceptors[Id, NettySyncServerOptions]) => default(ci.interceptors)
    ).serverLog(defaultServerLog)
  }

  private val log = LoggerFactory.getLogger(getClass.getName)

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
