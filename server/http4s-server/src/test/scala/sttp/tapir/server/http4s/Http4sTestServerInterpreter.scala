package sttp.tapir.server.http4s

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import cats.syntax.all._
import com.comcast.ip4s
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.{HttpApp, HttpRoutes}
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.http4s.Http4sTestServerInterpreter._
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.tests._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object Http4sTestServerInterpreter {
  type Routes = WebSocketBuilder2[IO] => HttpRoutes[IO]
}

class Http4sTestServerInterpreter extends TestServerInterpreter[IO, Fs2Streams[IO] with WebSockets, Http4sServerOptions[IO], Routes] {
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  override def route(es: List[ServerEndpoint[Fs2Streams[IO] with WebSockets, IO]], interceptors: Interceptors): Routes = {
    val serverOptions: Http4sServerOptions[IO] = interceptors(Http4sServerOptions.customiseInterceptors[IO]).options
    Http4sServerInterpreter(serverOptions).toWebSocketRoutes(es)
  }

  override def server(
      routes: NonEmptyList[Routes],
      gracefulShutdownTimeout: Option[FiniteDuration]
  ): Resource[IO, Port] = {
    val service: WebSocketBuilder2[IO] => HttpApp[IO] =
      wsb => routes.map(_.apply(wsb)).reduceK.orNotFound
    gracefulShutdownTimeout
      .foldLeft(
        EmberServerBuilder
          .default[IO]
          .withPort(ip4s.Port.fromInt(0).get)
          .withHttpWebSocketApp(service)
      )(_.withShutdownTimeout)
      .build
      .map(_.address.getPort)
  }
}
