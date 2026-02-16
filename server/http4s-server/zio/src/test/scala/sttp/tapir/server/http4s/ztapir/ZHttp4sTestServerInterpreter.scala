package sttp.tapir.server.http4s.ztapir

import cats.effect.{IO, Resource}
import cats._
import com.comcast.ip4s
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.{HttpApp, HttpRoutes}
import sttp.capabilities.WebSockets
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.server.http4s.Http4sServerOptions
import sttp.tapir.server.http4s.ztapir.ZHttp4sTestServerInterpreter._
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.tests._
import sttp.tapir.ztapir.ZServerEndpoint
import zio.Task
import zio.interop._
import zio.interop.catz._
import zio.interop.catz.implicits._

import scala.concurrent.duration.{DurationInt, FiniteDuration}

object ZHttp4sTestServerInterpreter {
  type F[A] = Task[A]
  type Routes = WebSocketBuilder2[F] => HttpRoutes[F]
  type ServerOptions = Http4sServerOptions[F]
}

class ZHttp4sTestServerInterpreter extends TestServerInterpreter[Task, ZioStreams with WebSockets, ServerOptions, Routes] {

  private val anyAvailablePort = ip4s.Port.fromInt(0).get
  private val serverBuilder = EmberServerBuilder.default[Task].withPort(anyAvailablePort)

  override def route(es: List[ZServerEndpoint[Any, ZioStreams with WebSockets]], interceptors: Interceptors): Routes = {
    val serverOptions: ServerOptions = interceptors(Http4sServerOptions.customiseInterceptors[Task]).options
    ZHttp4sServerInterpreter(serverOptions).fromWebSocket(es).toRoutes
  }

  override def server(
      route: Routes,
      gracefulShutdownTimeout: Option[FiniteDuration]
  ): Resource[IO, Port] = {
    val service: WebSocketBuilder2[Task] => HttpApp[Task] =
      wsb => route(wsb).orNotFound
    serverBuilder
      .withHttpWebSocketApp(service)
      .withShutdownTimeout(
        gracefulShutdownTimeout.getOrElse(0.seconds) // no need to wait unless it's explicitly required by test
      )
      .build
      .map(_.address.getPort)
      .mapK(new ~>[Task, IO] {
        // Converting a ZIO effect to an Cats Effect IO effect
        def apply[B](fa: Task[B]): IO[B] = fa.toEffect[IO]
      })
  }
}
