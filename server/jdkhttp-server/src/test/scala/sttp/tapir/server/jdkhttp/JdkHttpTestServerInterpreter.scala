package sttp.tapir.server.jdkhttp
import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.tests.Port

import java.net.InetSocketAddress
import scala.annotation.tailrec

class JdkHttpTestServerInterpreter() extends TestServerInterpreter[Id, Any, JdkHttpServerOptions, HttpHandler] {
  override def route(es: List[ServerEndpoint[Any, Id]], interceptors: Interceptors): HttpHandler = {
    val serverOptions: JdkHttpServerOptions =
      interceptors(JdkHttpServerOptions.customiseInterceptors).options.copy(send404WhenRequestNotHandled = false)
    JdkHttpServerInterpreter(serverOptions).toHandler(es)
  }

  override def server(routes: NonEmptyList[HttpHandler]): Resource[IO, Port] = {
    val server = IO.blocking {
      val server = HttpServer.create(new InetSocketAddress(0), 0)

      // some tests return multiple handlers for the same context path; hence, we need to manually manage this case
      val combinedHandler = new HttpHandler {
        override def handle(exchange: HttpExchange): Unit = {
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

      server.createContext("/", combinedHandler)
      server.start()
      server
    }

    Resource
      .make(server)(server => IO.blocking(server.stop(0)))
      .map(server => server.getAddress.getPort)
  }
}
