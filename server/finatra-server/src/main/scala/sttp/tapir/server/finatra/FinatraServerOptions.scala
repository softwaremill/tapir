package sttp.tapir.server.finatra

import com.twitter.util.logging.Logging
import com.twitter.util.{Future, FuturePool}
import sttp.tapir.server.interceptor.log.{DefaultServerLog, ServerLog, ServerLogInterceptor}
import sttp.tapir.server.interceptor.{CustomInterceptors, Interceptor}
import sttp.tapir.{Defaults, TapirFile}

import java.io.FileOutputStream

case class FinatraServerOptions(
    createFile: Array[Byte] => Future[TapirFile],
    deleteFile: TapirFile => Future[Unit],
    interceptors: List[Interceptor[Future]]
)

object FinatraServerOptions extends Logging {

  /** Allows customising the interceptors used by the server interpreter. */
  def customInterceptors: CustomInterceptors[Future, Unit, FinatraServerOptions] =
    CustomInterceptors(
      createLogInterceptor = (sl: ServerLog[Unit]) => new ServerLogInterceptor[Unit, Future](sl, (_: Unit, _) => Future.Done),
      createOptions = (ci: CustomInterceptors[Future, Unit, FinatraServerOptions]) =>
        FinatraServerOptions(defaultCreateFile(futurePool), defaultDeleteFile(futurePool), ci.interceptors)
    ).serverLog(defaultServerLog)

  val default: FinatraServerOptions = customInterceptors.options

  def defaultCreateFile(futurePool: FuturePool)(bytes: Array[Byte]): Future[TapirFile] = {
    // TODO: Make this streaming
    futurePool {
      val file = Defaults.createTempFile()
      val outputStream = new FileOutputStream(file.toFile)
      outputStream.write(bytes)
      outputStream.close()
      file: TapirFile
    }
  }

  def defaultDeleteFile(futurePool: FuturePool): TapirFile => Future[Unit] = file => { futurePool { Defaults.deleteFile()(file) } }

  private[finatra] lazy val futurePool = FuturePool.unboundedPool

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
