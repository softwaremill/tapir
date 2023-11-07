package sttp.tapir.server.nima

import sttp.tapir.{Defaults, TapirFile}
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interceptor.log.{DefaultServerLog, ServerLog}
import sttp.tapir.server.interceptor.{CustomiseInterceptors, Interceptor}

import java.util.logging.{Level, Logger}

case class NimaServerOptions(
    interceptors: List[Interceptor[Id]],
    createFile: ServerRequest => TapirFile,
    deleteFile: TapirFile => Unit
) {
  def prependInterceptor(i: Interceptor[Id]): NimaServerOptions = copy(interceptors = i :: interceptors)
  def appendInterceptor(i: Interceptor[Id]): NimaServerOptions = copy(interceptors = interceptors :+ i)
}

object NimaServerOptions {
  val Default: NimaServerOptions = customiseInterceptors.options

  private def default(interceptors: List[Interceptor[Id]]): NimaServerOptions =
    NimaServerOptions(interceptors, _ => Defaults.createTempFile(), Defaults.deleteFile())

  def customiseInterceptors: CustomiseInterceptors[Id, NimaServerOptions] =
    CustomiseInterceptors(
      createOptions = (ci: CustomiseInterceptors[Id, NimaServerOptions]) => default(ci.interceptors)
    ).serverLog(defaultServerLog)

  private val log = Logger.getLogger(classOf[NimaServerInterpreter].getName)

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
