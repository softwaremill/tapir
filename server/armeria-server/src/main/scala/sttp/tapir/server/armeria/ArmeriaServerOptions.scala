package sttp.tapir.server.armeria

import com.linecorp.armeria.common.CommonPools
import com.linecorp.armeria.server.ServiceRequestContext
import org.slf4j.{Logger, LoggerFactory}
import scala.concurrent.{Future, Promise}
import scala.util.control.NonFatal
import sttp.tapir.{Defaults, TapirFile}
import sttp.tapir.server.interceptor.Interceptor
import sttp.tapir.server.interceptor.log.{DefaultServerLog, ServerLog}

trait ArmeriaServerOptions[F[_]] {
  def createFile: () => F[TapirFile]
  def deleteFile: TapirFile => F[Unit]
  def interceptors: List[Interceptor[F]]
}

object ArmeriaServerOptions {

  private val logger: Logger = LoggerFactory.getLogger(this.getClass.getPackage.getName)

  def defaultCreateFile(): Future[TapirFile] = blocking(Defaults.createTempFile())

  def defaultDeleteFile(file: TapirFile): Future[Unit] = blocking(Defaults.deleteFile()(file))

  val defaultServerLog: ServerLog[Future] = DefaultServerLog[Future](
    doLogWhenHandled = debugLog,
    doLogAllDecodeFailures = debugLog,
    doLogExceptions = (msg: String, ex: Throwable) => Future.successful(logger.warn(msg, ex)),
    noLog = Future.unit
  )

  private def debugLog(msg: String, exOpt: Option[Throwable]): Future[Unit] =
    Future.successful(exOpt match {
      case None     => logger.debug(msg)
      case Some(ex) => logger.debug(msg, ex)
    })

  private def blocking[T](body: => T): Future[T] = {
    val promise = Promise[T]()
    CommonPools
      .blockingTaskExecutor()
      .execute(() => {
        try {
          promise.success(body)
        } catch {
          case NonFatal(ex) => promise.failure(ex)
        }
      })
    promise.future
  }
}
