package sttp.tapir.server.jdkhttp

import com.sun.net.httpserver.HttpsConfigurator
import sttp.tapir.{Defaults, Identity, TapirFile}
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interceptor.log.{DefaultServerLog, ServerLog}
import sttp.tapir.server.interceptor.{CustomiseInterceptors, Interceptor}

import java.util.concurrent.Executor
import java.util.logging.{Level, Logger}

/** @param send404WhenRequestNotHandled
  *   Should a 404 response be sent, when the request hasn't been handled. This is a safe default, but if there are multiple handlers for
  *   the same context path, this should be set to `false`. In that case, you can verify if the request has been handled using
  *   [[JdkHttpServerInterpreter.isRequestHandled]].
  *
  * @param basePath
  *   Path under which endpoints will be mounted when mounted on JdkHttpServer instance. I.e.: basePath of '/api' and endpoint '/hello' will
  *   result with a real path of '/api/hello'.
  *
  * @param port
  *   IP port to which JdkHttpServer instance will be bound. Default is 0, which means any random port provided by the OS.
  *
  * @param host
  *   Hostname or IP address (ie.: localhost, 0.0.0.0) to which JdkHttpServer instance will be bound. Default is 0.0.0.0 which binds the
  *   server to all network interfaces available on the OS.
  *
  * @param executor
  *   Optional executor to be used to dispatch HTTP requests. By default JDK http server uses calling thread executor so only single thread
  *   is being used for all the work. This might be fine for toy projects but for any actual load it is recommended to use an Executor built
  *   by JdkHttpServerOptions.httpExecutor utility method or by providing an executor on your own. If running with JDK 19+ one can provide
  *   the Virtual Thread executor to leverage Project Loom.
  *
  * @param httpsConfigurator
  *   Optional HTTPS configuration. Takes an instance of [[com.sun.net.httpserver.HttpsConfigurator]], which is a thin wrapper around
  *   [[javax.net.ssl.SSLContext]] to configure the SSL termination for this server.
  *
  * @param backlogSize
  *   Sets the size of server's tcp connection backlog. This is the maximum number of queued incoming connections to allow on the listening
  *   socket. Queued TCP connections exceeding this limit may be rejected by the TCP implementation. If set to 0 or less the system default
  *   for backlog size will be used. Default is 0.
  *
  * @param multipartFileThresholdBytes
  *   Sets the threshold of bytes of a multipart upload to trigger writing the multipart contents to a temporary file rather than keeping it
  *   entirely in memory. Default is 50MB.
  */
case class JdkHttpServerOptions(
                                 interceptors: List[Interceptor[Identity]],
                                 createFile: ServerRequest => TapirFile,
                                 deleteFile: TapirFile => Unit,
                                 send404WhenRequestNotHandled: Boolean = true,
                                 basePath: String = "/",
                                 port: Int = 0,
                                 host: String = "0.0.0.0",
                                 executor: Option[Executor] = None,
                                 httpsConfigurator: Option[HttpsConfigurator] = None,
                                 backlogSize: Int = 0,
                                 multipartFileThresholdBytes: Long = 52_428_800
) {
  require(0 <= port && port <= 65535, "Port has to be in 1-65535 range or 0 if random!")
  def prependInterceptor(i: Interceptor[Identity]): JdkHttpServerOptions = copy(interceptors = i :: interceptors)
  def appendInterceptor(i: Interceptor[Identity]): JdkHttpServerOptions = copy(interceptors = interceptors :+ i)
}

object JdkHttpServerOptions {
  val Default: JdkHttpServerOptions = customiseInterceptors.options

  import java.util.concurrent.{LinkedBlockingQueue, ThreadPoolExecutor, TimeUnit}

  def httpExecutor(maxPoolSize: Int, minPoolSize: Int = 1, threadTimeout: Int = 3000): Executor = {
    // Core pool size is set using minPoolSize to ensure at least minPoolSize threads are always ready
    val corePoolSize = minPoolSize

    new ThreadPoolExecutor(
      corePoolSize,
      maxPoolSize,
      threadTimeout,
      TimeUnit.MILLISECONDS,
      new LinkedBlockingQueue[Runnable]
    )
  }

  private def default(interceptors: List[Interceptor[Identity]]): JdkHttpServerOptions =
    JdkHttpServerOptions(interceptors, _ => Defaults.createTempFile(), Defaults.deleteFile())

  def customiseInterceptors: CustomiseInterceptors[Identity, JdkHttpServerOptions] =
    CustomiseInterceptors(
      createOptions = (ci: CustomiseInterceptors[Identity, JdkHttpServerOptions]) => default(ci.interceptors)
    ).serverLog(defaultServerLog)

  private val log = Logger.getLogger(classOf[JdkHttpServerInterpreter].getName)

  lazy val defaultServerLog: ServerLog[Identity] =
    DefaultServerLog[Identity](
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
