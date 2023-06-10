package sttp.tapir.server.jdkhttp

import com.sun.net.httpserver.HttpsConfigurator
import sttp.tapir.{Defaults, TapirFile}
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interceptor.log.{DefaultServerLog, ServerLog}
import sttp.tapir.server.interceptor.{CustomiseInterceptors, Interceptor}

import java.util.concurrent.Executor
import java.util.logging.{Level, Logger}

/** @param send404WhenRequestNotHandled
  *   Should a 404 response be sent, when the request hasn't been handled. This is a safe default, but if there are multiple handlers for
  *   the same context path, this should be set to `false`. In that case, you can verify if the request has been handled using
  *   [[JdkHttpServerInterpreter.isRequestHandled]].
  */
case class JdkHttpServerOptions(
    interceptors: List[Interceptor[Id]],
    createFile: ServerRequest => TapirFile,
    deleteFile: TapirFile => Unit,
    send404WhenRequestNotHandled: Boolean = true,
    basePath: String = "/",
    port: Int = 0,
    host: String = "0.0.0.0",
    executor: Option[Executor] = None,
    httpsConfigurator: Option[HttpsConfigurator] = None,
    backlogSize: Int = 0
) {
  require(0 <= port && port <= 65535, "Port has to be in 1-65535 range or 0 if random!")
  def prependInterceptor(i: Interceptor[Id]): JdkHttpServerOptions = copy(interceptors = i :: interceptors)
  def appendInterceptor(i: Interceptor[Id]): JdkHttpServerOptions = copy(interceptors = interceptors :+ i)
}

object JdkHttpServerOptions {
  val Default: JdkHttpServerOptions = customiseInterceptors.options

  private def default(interceptors: List[Interceptor[Id]]): JdkHttpServerOptions =
    JdkHttpServerOptions(interceptors, _ => Defaults.createTempFile(), Defaults.deleteFile())

  def customiseInterceptors: CustomiseInterceptors[Id, JdkHttpServerOptions] =
    CustomiseInterceptors(
      createOptions = (ci: CustomiseInterceptors[Id, JdkHttpServerOptions]) => default(ci.interceptors)
    ).serverLog(defaultServerLog)

  private val log = Logger.getLogger(classOf[JdkHttpServerInterpreter].getName)

  lazy val defaultServerLog: ServerLog[Id] =
    DefaultServerLog[Id](
      doLogWhenReceived = debugLog(_, None),
      doLogWhenHandled = debugLog,
      doLogAllDecodeFailures = debugLog,
      doLogExceptions = (msg: String, ex: Throwable) => log.log(Level.SEVERE, msg, ex),
      noLog = ()
    )

  private def debugLog(msg: String, exOpt: Option[Throwable]): Unit = exOpt match {
    case Some(e) => log.log(Level.FINE, msg, e)
    case None    => log.log(Level.FINE, msg)
  }
}
