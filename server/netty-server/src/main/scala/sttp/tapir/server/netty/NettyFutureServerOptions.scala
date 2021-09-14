package sttp.tapir.server.netty

import com.typesafe.scalalogging.Logger
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interceptor.log.{DefaultServerLog, ServerLog, ServerLogInterceptor}
import sttp.tapir.server.interceptor.{CustomInterceptors, Interceptor}
import sttp.tapir.{Defaults, TapirFile}

import scala.concurrent.{Future, blocking}

case class NettyFutureServerOptions(
    host: String,
    port: Int,
    interceptors: List[Interceptor[Future]],
    createFile: ServerRequest => Future[TapirFile],
    deleteFile: TapirFile => Future[Unit],
    nettyOptions: NettyOptions
) {
  def host(s: String): NettyFutureServerOptions = copy(host = s)
  def port(p: Int): NettyFutureServerOptions = copy(port = p)
  def randomPort: NettyFutureServerOptions = port(0)
  def prependInterceptor(i: Interceptor[Future]): NettyFutureServerOptions = copy(interceptors = i :: interceptors)
  def appendInterceptor(i: Interceptor[Future]): NettyFutureServerOptions = copy(interceptors = interceptors :+ i)
  def nettyOptions(o: NettyOptions): NettyFutureServerOptions = copy(nettyOptions = o)
}

object NettyFutureServerOptions {
  def default(interceptors: List[Interceptor[Future]]): NettyFutureServerOptions = NettyFutureServerOptions(
    "localhost",
    8080,
    interceptors,
    _ => {
      import scala.concurrent.ExecutionContext.Implicits.global
      Future(blocking(Defaults.createTempFile()))
    },
    file => {
      import scala.concurrent.ExecutionContext.Implicits.global
      Future(blocking(Defaults.deleteFile()(file)))
    },
    NettyOptions.default
  )

  lazy val defaultServerLog: ServerLog[Logger => Future[Unit]] = DefaultServerLog(
    doLogWhenHandled = debugLog,
    doLogAllDecodeFailures = debugLog,
    doLogExceptions = (msg: String, ex: Throwable) => log => Future.successful(log.error(msg, ex)),
    noLog = _ => Future.unit
  )

  def customInterceptors: CustomInterceptors[Future, Logger => Future[Unit], NettyFutureServerOptions] = {
    CustomInterceptors(
      createLogInterceptor =
        (sl: ServerLog[Logger => Future[Unit]]) => new ServerLogInterceptor[Logger => Future[Unit], Future](sl, (_, _) => Future.unit),
      createOptions = (ci: CustomInterceptors[Future, Logger => Future[Unit], NettyFutureServerOptions]) => default(ci.interceptors)
    ).serverLog(defaultServerLog)
  }

  private def debugLog(msg: String, exOpt: Option[Throwable]): Logger => Future[Unit] = log =>
    Future.successful {
      exOpt match {
        case None     => log.debug(msg)
        case Some(ex) => log.debug(s"$msg; exception: {}", ex)
      }
    }

  val default: NettyFutureServerOptions = customInterceptors.options
}
