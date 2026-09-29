package sttp.tapir.server.http4s

import cats.effect.{IO, Resource}
import com.comcast.ip4s
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Server
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.{HttpApp, HttpRoutes}
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.http4s.Http4sTestServerInterpreter._
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.tests._

import scala.concurrent.duration._

object Http4sTestServerInterpreter {
  type Routes = WebSocketBuilder2[IO] => HttpRoutes[IO]
}

class Http4sTestServerInterpreter extends TestServerInterpreter[IO, Fs2Streams[IO] with WebSockets, Http4sServerOptions[IO], Routes] {

  override def route(es: List[ServerEndpoint[Fs2Streams[IO] with WebSockets, IO]], interceptors: Interceptors): Routes = {
    val serverOptions: Http4sServerOptions[IO] = interceptors(Http4sServerOptions.customiseInterceptors[IO]).options
    Http4sServerInterpreter(serverOptions).toWebSocketRoutes(es)
  }

  private val anyAvailablePort = ip4s.Port.fromInt(0).get
  private val serverBuilder = EmberServerBuilder
    .default[IO]
    .withPort(anyAvailablePort)
    .withAdditionalSocketOptions(
      List(fs2.io.net.SocketOption.noDelay(true)) // https://github.com/http4s/http4s/issues/7668
    )

  def buildServer(
      makeService: WebSocketBuilder2[IO] => HttpApp[IO],
      gracefulShutdownTimeout: Option[FiniteDuration] = None
  ): Resource[IO, Server] =
    serverBuilder
      .withHttpWebSocketApp(makeService)
      .withShutdownTimeout(
        gracefulShutdownTimeout.getOrElse(0.seconds) // no need to wait unless it's explicitly required by test
      )
      .build

  override def server(
      route: Routes,
      gracefulShutdownTimeout: Option[FiniteDuration]
  ): Resource[IO, Port] =
    buildServer(wsb => route(wsb).orNotFound, gracefulShutdownTimeout)
      .map(_.address.getPort)
}
