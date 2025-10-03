package sttp.tapir.server.netty.zio

import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interceptor.log.DefaultServerLog
import sttp.tapir.server.interceptor.reject.DefaultRejectHandler
import sttp.tapir.server.interceptor.{CustomiseInterceptors, Interceptor}
import sttp.tapir.server.netty.NettyServerOptions
import sttp.tapir.{Defaults, TapirFile}
import zio.{Cause, RIO, ZIO}

/** Options configuring the [[NettyZioServerInterpreter]], which is being used by [[NettyZioServer]] to interpret tapir's
  * [[sttp.tapir.server.ServerEndpoint]]s so that they can be served using a Netty server. Contains the interceptors stack and functions for
  * file handling.
  */
case class NettyZioServerOptions[R](
    interceptors: List[Interceptor[RIO[R, *]]],
    createFile: ServerRequest => RIO[R, TapirFile],
    deleteFile: TapirFile => RIO[R, Unit],
    multipartTempDirectory: Option[TapirFile],
    multipartMinSizeForDisk: Option[Long]
) extends NettyServerOptions[RIO[R, *]] {
  def prependInterceptor(i: Interceptor[RIO[R, *]]): NettyZioServerOptions[R] = copy(interceptors = i :: interceptors)
  def appendInterceptor(i: Interceptor[RIO[R, *]]): NettyZioServerOptions[R] = copy(interceptors = interceptors :+ i)
  def widen[R2 <: R]: NettyZioServerOptions[R2] = this.asInstanceOf[NettyZioServerOptions[R2]]
}

object NettyZioServerOptions {

  def default[R]: NettyZioServerOptions[R] = customiseInterceptors.options

  private def default[R](
      interceptors: List[Interceptor[RIO[R, *]]]
  ): NettyZioServerOptions[R] =
    NettyZioServerOptions(
      interceptors,
      _ => ZIO.attemptBlocking(Defaults.createTempFile()),
      file => ZIO.attemptBlocking(Defaults.deleteFile()(file)),
      None,
      None
    )

  def customiseInterceptors[R]: CustomiseInterceptors[RIO[R, *], NettyZioServerOptions[R]] =
    CustomiseInterceptors(
      createOptions = (ci: CustomiseInterceptors[RIO[R, *], NettyZioServerOptions[R]]) => default(ci.interceptors)
    ).serverLog(defaultServerLog[R]).rejectHandler(DefaultRejectHandler.orNotFound[RIO[R, *]])

  def defaultServerLog[R]: DefaultServerLog[RIO[R, *]] = DefaultServerLog(
    doLogWhenReceived = debugLog(_, None),
    doLogWhenHandled = debugLog[R],
    doLogAllDecodeFailures = debugLog[R],
    doLogExceptions = (msg: String, ex: Throwable) => ZIO.logErrorCause(msg, Cause.die(ex)),
    noLog = ZIO.unit
  )

  private def debugLog[R](msg: String, exOpt: Option[Throwable]): RIO[R, Unit] =
    exOpt match {
      case None     => ZIO.logDebug(msg)
      case Some(ex) => ZIO.logDebugCause(msg, Cause.fail(ex))
    }

}
