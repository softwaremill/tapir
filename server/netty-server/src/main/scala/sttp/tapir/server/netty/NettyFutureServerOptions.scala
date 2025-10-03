package sttp.tapir.server.netty

import org.slf4j.LoggerFactory
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interceptor.log.DefaultServerLog
import sttp.tapir.server.interceptor.reject.DefaultRejectHandler
import sttp.tapir.server.interceptor.{CustomiseInterceptors, Interceptor}
import sttp.tapir.server.netty.internal.NettyDefaults
import sttp.tapir.{Defaults, TapirFile}

import scala.concurrent.{Future, blocking}

/** Options configuring the [[NettyFutureServerInterpreter]], which is being used by [[NettyFutureServer]] to interpret tapir's
  * [[sttp.tapir.server.ServerEndpoint]]s so that they can be served using a Netty server. Contains the interceptors stack and functions for
  * file handling.
  */
case class NettyFutureServerOptions(
    interceptors: List[Interceptor[Future]],
    createFile: ServerRequest => Future[TapirFile],
    deleteFile: TapirFile => Future[Unit],
    multipartTempDirectory: Option[TapirFile],
    multipartMinSizeForDisk: Option[Long]
) extends NettyServerOptions[Future] {
  def prependInterceptor(i: Interceptor[Future]): NettyFutureServerOptions = copy(interceptors = i :: interceptors)
  def appendInterceptor(i: Interceptor[Future]): NettyFutureServerOptions = copy(interceptors = interceptors :+ i)
}

object NettyFutureServerOptions {

  def default: NettyFutureServerOptions = customiseInterceptors.options

  private def default(interceptors: List[Interceptor[Future]]): NettyFutureServerOptions =
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
      None,
      None
    )

  /** Customise the interceptors that are being used when exposing endpoints as a server. */
  def customiseInterceptors: CustomiseInterceptors[Future, NettyFutureServerOptions] = {
    CustomiseInterceptors(
      createOptions = (ci: CustomiseInterceptors[Future, NettyFutureServerOptions]) => default(ci.interceptors)
    ).serverLog(defaultServerLog).rejectHandler(DefaultRejectHandler.orNotFound[Future])
  }

  private val log = LoggerFactory.getLogger(getClass.getName)

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
