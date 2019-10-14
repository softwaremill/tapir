package tapir.server.finatra
import java.io.{File, FileOutputStream}

import com.twitter.finagle.http.Request
import com.twitter.util.{Future, FuturePool}
import tapir.Defaults
import tapir.server.{DecodeFailureHandler, LoggingOptions, ServerDefaults}

case class FinatraServerOptions(
    createFile: Array[Byte] => Future[File],
    decodeFailureHandler: DecodeFailureHandler[Request],
    loggingOptions: LoggingOptions
)

object FinatraServerOptions {
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

  private val futurePool = FuturePool.unboundedPool

  implicit val default: FinatraServerOptions = FinatraServerOptions(
    defaultCreateFile(futurePool),
    ServerDefaults.decodeFailureHandler,
    LoggingOptions.default
  )
}
