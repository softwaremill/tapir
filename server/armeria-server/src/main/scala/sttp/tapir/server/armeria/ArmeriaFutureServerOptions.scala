package sttp.tapir.server.armeria

import com.linecorp.armeria.common.CommonPools
import org.slf4j.{Logger, LoggerFactory}
import scala.concurrent.{Future, Promise}
import scala.util.control.NonFatal
import sttp.tapir.{Defaults, TapirFile}
import sttp.tapir.server.interceptor.log.DefaultServerLog
import sttp.tapir.server.interceptor.{CustomiseInterceptors, Interceptor}

final case class ArmeriaFutureServerOptions(
    createFile: () => Future[TapirFile],
    deleteFile: TapirFile => Future[Unit],
    interceptors: List[Interceptor[Future]]
) extends ArmeriaServerOptions[Future] {
  def prependInterceptor(i: Interceptor[Future]): ArmeriaFutureServerOptions = copy(interceptors = i :: interceptors)

  def appendInterceptor(i: Interceptor[Future]): ArmeriaFutureServerOptions = copy(interceptors = interceptors :+ i)
}

object ArmeriaFutureServerOptions {

  val defaultServerLog: DefaultServerLog[Future] = DefaultServerLog[Future](
    doLogWhenReceived = debugLog(_, None),
    doLogWhenHandled = debugLog,
    doLogAllDecodeFailures = debugLog,
    doLogExceptions = (msg: String, ex: Throwable) => Future.successful(logger.warn(msg, ex)),
    noLog = Future.unit
  )

  /** Allows customising the interceptors used by the server interpreter. */
  def customiseInterceptors: CustomiseInterceptors[Future, ArmeriaFutureServerOptions] =
    CustomiseInterceptors(
      createOptions = (ci: CustomiseInterceptors[Future, ArmeriaFutureServerOptions]) =>
        ArmeriaFutureServerOptions(
          defaultCreateFile,
          defaultDeleteFile,
          ci.interceptors
        )
    ).serverLog(defaultServerLog)

  val default: ArmeriaFutureServerOptions = customiseInterceptors.options

  private val logger: Logger = LoggerFactory.getLogger(this.getClass.getPackage.getName)

  def defaultCreateFile(): Future[TapirFile] = blocking(Defaults.createTempFile())

  def defaultDeleteFile(file: TapirFile): Future[Unit] = blocking(Defaults.deleteFile()(file))

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
