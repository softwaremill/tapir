package sttp.tapir.server.finatra

import java.io.{File, FileOutputStream}
import com.twitter.util.logging.Logging
import com.twitter.util.{Future, FuturePool}
import sttp.tapir.Defaults
import sttp.tapir.server.interceptor.Interceptor
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureHandler, DecodeFailureInterceptor, DefaultDecodeFailureHandler}
import sttp.tapir.server.interceptor.exception.{DefaultExceptionHandler, ExceptionHandler, ExceptionInterceptor}
import sttp.tapir.server.interceptor.log.{DefaultServerLog, ServerLog, ServerLogInterceptor}

case class FinatraServerOptions(
    createFile: Array[Byte] => Future[File],
    interceptors: List[Interceptor[Future, FinatraContent]]
)

object FinatraServerOptions extends Logging {

  /** Creates default [[FinatraServerOptions]] with custom interceptors, sitting between an optional exception
    * interceptor, optional logging interceptor, and the ultimate decode failure handling interceptor.
    *
    * The options can be then further customised using copy constructors or the methods to append/prepend
    * interceptors.
    *
    * @param serverLog The server log using which an interceptor will be created, if any.
    * @param additionalInterceptors Additional interceptors, e.g. handling decode failures, or providing alternate
    *                               responses.
    * @param decodeFailureHandler The decode failure handler, from which an interceptor will be created.
    */
  def customInterceptors(
      exceptionHandler: Option[ExceptionHandler] = Some(DefaultExceptionHandler),
      serverLog: Option[ServerLog[Unit]] = Some(defaultServerLog),
      additionalInterceptors: List[Interceptor[Future, FinatraContent]] = Nil,
      decodeFailureHandler: DecodeFailureHandler = DefaultDecodeFailureHandler.handler
  ): FinatraServerOptions =
    FinatraServerOptions(
      defaultCreateFile(futurePool),
      exceptionHandler.map(new ExceptionInterceptor[Future, FinatraContent](_)).toList ++
        serverLog.map(sl => new ServerLogInterceptor[Unit, Future, FinatraContent](sl, (_: Unit, _) => Future.Done)).toList ++
        additionalInterceptors ++
        List(new DecodeFailureInterceptor[Future, FinatraContent](decodeFailureHandler))
    )

  implicit val default: FinatraServerOptions = customInterceptors()

  def defaultCreateFile(futurePool: FuturePool)(bytes: Array[Byte]): Future[File] = {
    // TODO: Make this streaming
    futurePool {
      val file = Defaults.createTempFile()
      val outputStream = new FileOutputStream(file)
      outputStream.write(bytes)
      outputStream.close()
      file
    }
  }

  private lazy val futurePool = FuturePool.unboundedPool

  lazy val defaultServerLog: ServerLog[Unit] = DefaultServerLog(
    doLogWhenHandled = debugLog,
    doLogAllDecodeFailures = debugLog,
    doLogExceptions = (msg: String, ex: Throwable) => error(msg, ex),
    noLog = ()
  )

  private def debugLog(msg: String, exOpt: Option[Throwable]): Unit =
    exOpt match {
      case None     => debug(msg)
      case Some(ex) => debug(msg, ex)
    }
}
