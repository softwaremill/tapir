package sttp.tapir.server.jdkhttp
import cats.effect.{IO, Resource}
import com.sun.net.httpserver.{HttpHandler, HttpServer}
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
      server.createContext("/", route)
      server.start()
      server
    }

    Resource
      .make(server)(s => IO.blocking(s.stop(gracefulShutdownTimeout.map(_.toSeconds.toInt).getOrElse(0))))
      .map(_.getAddress.getPort)
  }
}
