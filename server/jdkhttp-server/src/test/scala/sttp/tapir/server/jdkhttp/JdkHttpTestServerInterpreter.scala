package sttp.tapir.server.jdkhttp
import cats.effect.{IO, Resource}
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.tests._

import java.net.InetSocketAddress
import scala.concurrent.duration.FiniteDuration

class JdkHttpTestServerInterpreter() extends TestServerInterpreter[Identity, Any, JdkHttpServerOptions, HttpHandler] {
  override def route(es: List[ServerEndpoint[Any, Identity]], interceptors: Interceptors): HttpHandler = {
    val serverOptions: JdkHttpServerOptions =
      interceptors(JdkHttpServerOptions.customiseInterceptors).options.copy(send404WhenRequestNotHandled = false)
    JdkHttpServerInterpreter(serverOptions).toHandler(es)
  }

  override def server(
      route: HttpHandler,
      gracefulShutdownTimeout: Option[FiniteDuration]
  ): Resource[IO, Port] = {
    val server = IO.blocking {
      val server = HttpServer.create(new InetSocketAddress(0), 0)

      // some tests return multiple handlers for the same context path; hence, we need to manually manage this case
      val handlerWith404Fallback = new HttpHandler {
        override def handle(exchange: HttpExchange): Unit = {
          route.handle(exchange)
          if (!JdkHttpServerInterpreter.isRequestHandled(exchange)) {
            try exchange.sendResponseHeaders(404, -1)
            finally exchange.close()
          }
        }
      }

      server.createContext("/", handlerWith404Fallback)

      server.start()
      server
    }

    Resource
      .make(server)(s => IO.blocking(s.stop(gracefulShutdownTimeout.map(_.toSeconds.toInt).getOrElse(0))))
      .map(_.getAddress.getPort)
  }
}
