package sttp.tapir.server.jdkhttp

import com.sun.net.httpserver._
import sttp.tapir.Identity
import sttp.tapir.server.ServerEndpoint

import java.net.InetSocketAddress
import java.util.concurrent.Executor

case class JdkHttpServer(
                          endpoints: Vector[ServerEndpoint[Any, Identity]] = Vector.empty,
                          options: JdkHttpServerOptions = JdkHttpServerOptions.Default
) {

  /** Sets the port to which the server will be bound. */
  def port(port: Int): JdkHttpServer = copy(options = options.copy(port = port))

  /** Sets the hostname to which the server will be bound. */
  def host(host: String): JdkHttpServer = copy(options = options.copy(host = host))

  /** Allows you to configure the Executor which will be used to handle HTTP requests. By default com.sun.net.httpserver.HttpServer uses a
    * single thread (a calling thread executor to be precise) executor to handle traffic which might be fine for local toy projects.
    *
    * If you intend to use this HTTP server for any deployments that will run under load it's absolutely necessary to set an executor that
    * will use proper thread pool to handle the load. Recommended approach is to use `JdkHttpServerOptions.httpExecutor` method to create a
    * ThreadPoolExecutor that will scale under load. You can also use an Executor returned from any of the constructors in the
    * `java.util.concurrent.Executors` class.
    *
    * Alternatively, if running with a JDK 19+ you can leverage Project Loom and use `Executors.newVirtualThreadPerTaskExecutor()` to run
    * each request on a virtual thread. This however means it is possible for your server to be overloaded with work as each request will be
    * given a thread with no backpressure on how many should be executed in parallel.
    */
  def executor(executor: Executor): JdkHttpServer = copy(options = options.copy(executor = Some(executor)))

  /** Sets the base path under which your endpoints will be mounted, so if you have an endpoint configured to respond to /hello path and
    * base path set to /api your endpoint will in fact respond under /api/hello path.
    *
    * The default base path is "/".
    */
  def basePath(path: String): JdkHttpServer = copy(options = options.copy(basePath = path))

  /** Sets the size of server's tcp connection backlog. This is the maximum number of queued incoming connections to allow on the listening
    * socket. Queued TCP connections exceeding this limit may be rejected by the TCP implementation. If set to 0 or less the system default
    * for backlog size will be used. Default is 0.
    */
  def backlogSize(size: Int): JdkHttpServer = copy(options = options.copy(backlogSize = size))

  /** Takes an instance of [[com.sun.net.httpserver.HttpsConfigurator]], which is a thin wrapper around [[javax.net.ssl.SSLContext]] to
    * configure the SSL termination for this server.
    */
  def https(httpsConfigurator: HttpsConfigurator): JdkHttpServer =
    copy(options = options.copy(httpsConfigurator = Some(httpsConfigurator)))

  /** Replace the options - any modifications to the host, prot, executor etc. will be replaced with what is configured in the given options
    * instance. Useful for customising interceptors.
    */
  def options(o: JdkHttpServerOptions): JdkHttpServer = copy(options = o)

  def addEndpoint(se: ServerEndpoint[Any, Identity]): JdkHttpServer = addEndpoints(List(se))

  def addEndpoints(ses: List[ServerEndpoint[Any, Identity]]): JdkHttpServer =
    copy(endpoints = endpoints ++ ses)

  /** Use this if you want to add more routes manually, outside of the routes defined with tapir. Afterwards it is necessary to call
    * `HttpServer.start()` to actually start the server.
    */
  def setup(): HttpServer = {
    val socketAddress = new InetSocketAddress(options.host, options.port)
    val server = options.httpsConfigurator match {
      case Some(httpsConfig) =>
        val server = HttpsServer.create(socketAddress, options.backlogSize)
        server.setHttpsConfigurator(httpsConfig)
        server
      case None =>
        HttpServer.create(socketAddress, options.backlogSize)
    }

    val handler = JdkHttpServerInterpreter(options).toHandler(endpoints.toList)

    options.executor.foreach { executor => server.setExecutor(executor) }

    server.createContext(options.basePath, handler)

    server
  }

  /** Setup and start the server in the background. */
  def start(): HttpServer = {
    val server = setup()
    server.start()
    server
  }
}
