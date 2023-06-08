package sttp.tapir.server.jdkhttp
import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import com.sun.net.httpserver.{HttpHandler, HttpServer}
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.tests.Port

import java.net.InetSocketAddress

class JdkHttpTestServerInterpreter() extends TestServerInterpreter[Id, Any, JdkHttpServerOptions, HttpHandler] {
  override def route(es: List[ServerEndpoint[Any, Id]], interceptors: Interceptors): HttpHandler = {
    val serverOptions: JdkHttpServerOptions = interceptors(JdkHttpServerOptions.customiseInterceptors).options
    JdkHttpServerInterpreter(serverOptions).toHandler(es)
  }

  override def server(routes: NonEmptyList[HttpHandler]): Resource[IO, Port] = {
    val server = IO.blocking {
      val server = HttpServer.create(new InetSocketAddress(0), 0)
      for (route <- routes.toList) {
        server.createContext("/", route)
      }
      server.start()
      server
    }

    Resource
      .make(server)(server => IO.blocking(server.stop(0)))
      .map(server => server.getAddress.getPort)
  }
}
