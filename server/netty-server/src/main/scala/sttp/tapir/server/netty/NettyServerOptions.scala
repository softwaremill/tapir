package sttp.tapir.server.netty

import scala.concurrent.Future

import com.typesafe.scalalogging.Logger
import sttp.tapir.{Defaults, TapirFile}
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interceptor.{CustomInterceptors, Interceptor}
import sttp.tapir.server.interceptor.log.{DefaultServerLog, ServerLog, ServerLogInterceptor}

case class NettyServerOptions(
    interceptors: List[Interceptor[Future]],
    createFile: ServerRequest => Future[TapirFile] = _ => Future.successful(Defaults.createTempFile())
)

object NettyServerOptions {

  lazy val defaultServerLog: ServerLog[Logger => Future[Unit]] = DefaultServerLog(
    doLogWhenHandled = debugLog,
    doLogAllDecodeFailures = debugLog,
    doLogExceptions = (msg: String, ex: Throwable) => log => Future.successful(log.error(msg, ex)),
    noLog = _ => Future.unit
  )

  def customInterceptors: CustomInterceptors[Future, Logger => Future[Unit], NettyServerOptions] = {
    CustomInterceptors(
      createLogInterceptor =
        (sl: ServerLog[Logger => Future[Unit]]) => new ServerLogInterceptor[Logger => Future[Unit], Future](sl, (_, _) => Future.unit),
      createOptions = (ci: CustomInterceptors[Future, Logger => Future[Unit], NettyServerOptions]) => NettyServerOptions(ci.interceptors)
    ).serverLog(defaultServerLog)
  }

  private def debugLog(msg: String, exOpt: Option[Throwable]): Logger => Future[Unit] = log =>
    Future.successful {
      exOpt match {
        case None     => log.debug(msg)
        case Some(ex) => log.debug(s"$msg; exception: {}", ex)
      }
    }

  val default: NettyServerOptions = customInterceptors.options
}
