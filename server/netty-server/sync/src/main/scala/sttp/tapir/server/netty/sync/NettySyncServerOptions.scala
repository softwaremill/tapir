package sttp.tapir.server.netty.sync

import org.slf4j.LoggerFactory
import sttp.shared.Identity
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interceptor.log.DefaultServerLog
import sttp.tapir.server.interceptor.reject.DefaultRejectHandler
import sttp.tapir.server.interceptor.{CustomiseInterceptors, Interceptor}
import sttp.tapir.server.netty.NettyServerOptions
import sttp.tapir.server.netty.internal.NettyDefaults
import sttp.tapir.{Defaults, TapirFile}

case class NettySyncServerOptions(
    interceptors: List[Interceptor[Identity]],
    createFile: ServerRequest => TapirFile,
    deleteFile: TapirFile => Unit,
    multipartTempDirectory: Option[TapirFile],
    multipartMinSizeForDisk: Option[Long],
    /** When a request is cancelled (due to client closing the connection or a timeout), should the server's logic be interrupted (using
      * `Thread.interrupt`)? This might be useful for long-running requests. However, if all requests are fast, it might have a net negative
      * impact: for example, when a database query is interrupted, the db connection becomes marked as broken, and will have to be
      * re-established.
      */
    interruptServerLogicWhenRequestCancelled: Boolean
) extends NettyServerOptions[Identity]:
  def prependInterceptor(i: Interceptor[Identity]): NettySyncServerOptions = copy(interceptors = i :: interceptors)
  def appendInterceptor(i: Interceptor[Identity]): NettySyncServerOptions = copy(interceptors = interceptors :+ i)

object NettySyncServerOptions:

  /** Default options, using TCP sockets (the most common case). This can be later customised using
    * [[NettySyncServerOptions#nettyOptions()]].
    */
  def default: NettySyncServerOptions = customiseInterceptors.options

  private def default(
      interceptors: List[Interceptor[Identity]]
  ): NettySyncServerOptions =
    NettySyncServerOptions(
      interceptors,
      _ => Defaults.createTempFile(),
      Defaults.deleteFile(),
      None,
      None,
      interruptServerLogicWhenRequestCancelled = true
    )

  /** Customise the interceptors that are being used when exposing endpoints as a server. */
  def customiseInterceptors: CustomiseInterceptors[Identity, NettySyncServerOptions] =
    CustomiseInterceptors(
      createOptions = (ci: CustomiseInterceptors[Identity, NettySyncServerOptions]) => default(ci.interceptors)
    ).serverLog(defaultServerLog).rejectHandler(DefaultRejectHandler.orNotFound)

  private val log = LoggerFactory.getLogger(getClass.getName)

  lazy val defaultServerLog: DefaultServerLog[Identity] =
    DefaultServerLog[Identity](
      doLogWhenReceived = debugLog(_, None),
      doLogWhenHandled = debugLog,
      doLogAllDecodeFailures = debugLog,
      doLogExceptions = (msg: String, e: Throwable) =>
        e match
          // if server logic is interrupted (e.g. due to timeout), this isn't an error, but might still be useful for debugging,
          // to know how far processing got
          case _: InterruptedException => log.debug(msg, e)
          case _                       => log.error(msg, e),
      noLog = ()
    )

  private def debugLog(msg: String, exOpt: Option[Throwable]): Unit = NettyDefaults.debugLog(log, msg, exOpt)
