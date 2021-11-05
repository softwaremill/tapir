package sttp.tapir.server.http4s

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import cats.syntax.all._
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.{HttpApp, HttpRoutes}
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.Endpoint
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureHandler, DefaultDecodeFailureHandler}
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.tests.Port
import Http4sTestServerInterpreter._
import org.http4s.server.websocket.WebSocketBuilder2

import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

object Http4sTestServerInterpreter {
  type Routes = WebSocketBuilder2[IO] => HttpRoutes[IO]
}

class Http4sTestServerInterpreter extends TestServerInterpreter[IO, Fs2Streams[IO] with WebSockets, Routes] {
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  override def route[I, E, O](
      e: ServerEndpoint[I, E, O, Fs2Streams[IO] with WebSockets, IO],
      decodeFailureHandler: Option[DecodeFailureHandler] = None,
      metricsInterceptor: Option[MetricsRequestInterceptor[IO]] = None
  ): Routes = {
    val serverOptions: Http4sServerOptions[IO, IO] = Http4sServerOptions
      .customInterceptors[IO, IO]
      .metricsInterceptor(metricsInterceptor)
      .decodeFailureHandler(decodeFailureHandler.getOrElse(DefaultDecodeFailureHandler.handler))
      .options
    Http4sServerInterpreter(serverOptions).toRoutesWithWebSockets(e)
  }

  override def route[I, E, O](es: List[ServerEndpoint[I, E, O, Fs2Streams[IO] with WebSockets, IO]]): Routes = {
    Http4sServerInterpreter[IO]().toRoutesWithWebSockets(es)
  }

  override def routeRecoverErrors[I, E <: Throwable, O](e: Endpoint[I, E, O, Fs2Streams[IO] with WebSockets], fn: I => IO[O])(implicit
      eClassTag: ClassTag[E]
  ): Routes = {
    Http4sServerInterpreter[IO]().toRouteWithWebSocketsRecoverErrors(e)(fn)
  }

  override def server(routes: NonEmptyList[Routes]): Resource[IO, Port] = {
    val service: WebSocketBuilder2[IO] => HttpApp[IO] =
      wsb => routes.map(_.apply(wsb)).reduceK.orNotFound

    BlazeServerBuilder[IO]
      .withExecutionContext(ExecutionContext.global)
      .bindHttp(0, "localhost")
      .withHttpWebSocketApp(service)
      .resource
      .map(_.address.getPort)
  }
}
