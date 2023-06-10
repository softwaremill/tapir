package sttp.tapir.server.jdkhttp

import com.sun.net.httpserver._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.jdkhttp.JdkHttpServer.combinedHandler

import java.net.InetSocketAddress

case class JdkHttpServer(
    handlers: Vector[HttpHandler] = Vector.empty,
    options: JdkHttpServerOptions = JdkHttpServerOptions.Default
) {

  def port(port: Int): JdkHttpServer = copy(options = options.copy(port = port))

  def host(host: String): JdkHttpServer = copy(options = options.copy(host = host))

  def addEndpoint(se: ServerEndpoint[Any, Id]): JdkHttpServer = addEndpoints(List(se))

  def addEndpoint(se: ServerEndpoint[Any, Id], overrideOptions: JdkHttpServerOptions): JdkHttpServer =
    addEndpoints(List(se), overrideOptions)

  def addEndpoints(ses: List[ServerEndpoint[Any, Id]]): JdkHttpServer = addHandler(
    JdkHttpServerInterpreter(options).toHandler(ses)
  )

  def addEndpoints(ses: List[ServerEndpoint[Any, Id]], overrideOptions: JdkHttpServerOptions): JdkHttpServer =
    addHandler(JdkHttpServerInterpreter(overrideOptions).toHandler(ses))

  def addHandler(r: HttpHandler): JdkHttpServer = copy(handlers = handlers :+ r)

  def addHandlers(r: Iterable[HttpHandler]): JdkHttpServer = copy(handlers = handlers ++ r)

  /** Use this if you want to add more routes manually, outside of the routes defined with tapir.
    *
    * @return
    *   com.sun.net.httpserver.HttpServer
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

    val handler = combinedHandler(handlers)

    options.executor.foreach { executor => server.setExecutor(executor) }

    server.createContext(options.basePath, handler)

    server
  }

  /** Setup and start the server.
    *
    * @return
    *   com.sun.net.httpserver.HttpServer
    */
  def start(): HttpServer = {
    val server = setup()
    server.start()
    server
  }
}

object JdkHttpServer {
  import scala.annotation.tailrec

  private def combinedHandler(routes: Vector[HttpHandler]): HttpHandler =
    (exchange: HttpExchange) => {
      @tailrec
      def doCombine(rs: List[HttpHandler]): Unit = rs match {
        case Nil =>
          try exchange.sendResponseHeaders(404, -1)
          finally exchange.close()
        case head :: tail =>
          head.handle(exchange)
          if (!JdkHttpServerInterpreter.isRequestHandled(exchange)) doCombine(tail)
      }

      doCombine(routes.toList)
    }

}
