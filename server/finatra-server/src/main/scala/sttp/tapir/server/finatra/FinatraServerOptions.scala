package sttp.tapir.server.finatra

import java.io.{File, FileOutputStream}

import com.twitter.util.logging.Logging
import com.twitter.util.{Future, FuturePool}
import sttp.tapir.Defaults
import sttp.tapir.server.{DecodeFailureHandler, LogRequestHandling, ServerDefaults}

case class FinatraServerOptions(
    createFile: Array[Byte] => Future[File],
    decodeFailureHandler: DecodeFailureHandler,
    logRequestHandling: LogRequestHandling[Unit]
)

object FinatraServerOptions extends Logging {
  implicit lazy val default: FinatraServerOptions = FinatraServerOptions(
    defaultCreateFile(futurePool),
    ServerDefaults.decodeFailureHandler,
    defaultLogRequestHandling
  )

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

  lazy val defaultLogRequestHandling: LogRequestHandling[Unit] = LogRequestHandling(
    doLogWhenHandled = debugLog,
    doLogAllDecodeFailures = debugLog,
    doLogLogicExceptions = (msg: String, ex: Throwable) => error(msg, ex),
    noLog = ()
  )

  private def debugLog(msg: String, exOpt: Option[Throwable]): Unit = exOpt match {
    case None     => debug(msg)
    case Some(ex) => debug(msg, ex)
  }
}
