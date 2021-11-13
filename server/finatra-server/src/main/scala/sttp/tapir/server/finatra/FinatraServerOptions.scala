package sttp.tapir.server.finatra

import com.twitter.util.logging.Logging
import com.twitter.util.{Future, FuturePool}
import sttp.tapir.{Defaults, TapirFile}
import sttp.tapir.server.interceptor.log.{DefaultServerLog, ServerLog, ServerLogInterceptor}
import sttp.tapir.server.interceptor.{CustomInterceptors, Interceptor}
import FinatraServerInterpreter.FutureMonadError

import java.io.FileOutputStream

case class FinatraServerOptions(
    createFile: Array[Byte] => Future[TapirFile],
    deleteFile: TapirFile => Future[Unit],
    interceptors: List[Interceptor[Future]]
)

object FinatraServerOptions extends Logging {

  /** Allows customising the interceptors used by the server interpreter. */
  def customInterceptors: CustomInterceptors[Future, FinatraServerOptions] =
    CustomInterceptors(
      createOptions = (ci: CustomInterceptors[Future, FinatraServerOptions]) =>
        FinatraServerOptions(defaultCreateFile(futurePool), defaultDeleteFile(futurePool), ci.interceptors)
    ).serverLog(defaultServerLog)

  val default: FinatraServerOptions = customInterceptors.options

  def defaultCreateFile(futurePool: FuturePool)(bytes: Array[Byte]): Future[TapirFile] = {
    // TODO: Make this streaming
    futurePool {
      val file = Defaults.createTempFile()
      val outputStream = new FileOutputStream(file)
      outputStream.write(bytes)
      outputStream.close()
      file
    }
  }

  def defaultDeleteFile(futurePool: FuturePool): TapirFile => Future[Unit] = file => { futurePool { Defaults.deleteFile()(file) } }

  private[finatra] lazy val futurePool = FuturePool.unboundedPool

  lazy val defaultServerLog: ServerLog[Future] = DefaultServerLog(
    doLogWhenHandled = debugLog,
    doLogAllDecodeFailures = debugLog,
    doLogExceptions = (msg: String, ex: Throwable) => futurePool { error(msg, ex) }
  )

  private def debugLog(msg: String, exOpt: Option[Throwable]): Future[Unit] = futurePool {
    exOpt match {
      case None     => debug(msg)
      case Some(ex) => debug(msg, ex)
    }
  }
}
