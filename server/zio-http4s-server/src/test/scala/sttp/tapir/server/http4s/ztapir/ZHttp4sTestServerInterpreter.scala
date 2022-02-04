package sttp.tapir.server.http4s.ztapir

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import cats.syntax.all._
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.{HttpApp, HttpRoutes}
import sttp.capabilities.WebSockets
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.server.http4s.Http4sServerOptions
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureHandler, DefaultDecodeFailureHandler}
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.tests.Port
import sttp.tapir.ztapir.ZServerEndpoint
import zio.RIO
import zio.Clock
import zio.interop.catz._
import zio.interop.catz.implicits._
import ZHttp4sTestServerInterpreter._
import org.http4s.server.websocket.WebSocketBuilder2

import scala.concurrent.ExecutionContext

object ZHttp4sTestServerInterpreter {
  type Routes = WebSocketBuilder2[RIO[Clock, *]] => HttpRoutes[RIO[Clock, *]]
}

class ZHttp4sTestServerInterpreter extends TestServerInterpreter[RIO[Clock, *], ZioStreams with WebSockets, Routes] {
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  override def route(
      e: ZServerEndpoint[Clock, ZioStreams with WebSockets],
      decodeFailureHandler: Option[DecodeFailureHandler] = None,
      metricsInterceptor: Option[MetricsRequestInterceptor[RIO[Clock, *]]] = None
  ): Routes = {
    val serverOptions: Http4sServerOptions[RIO[Clock, *], RIO[Clock, *]] = Http4sServerOptions
      .customInterceptors[RIO[Clock, *], RIO[Clock, *]]
      .metricsInterceptor(metricsInterceptor)
      .decodeFailureHandler(decodeFailureHandler.getOrElse(DefaultDecodeFailureHandler.default))
      .options

    ZHttp4sServerInterpreter(serverOptions).fromWebSocket(e).toRoutes
  }

  override def route(
      es: List[ZServerEndpoint[Clock, ZioStreams with WebSockets]]
  ): Routes = {
    ZHttp4sServerInterpreter().fromWebSocket(es).toRoutes
  }

  override def server(routes: NonEmptyList[Routes]): Resource[IO, Port] = {
    val service: WebSocketBuilder2[RIO[Clock, *]] => HttpApp[RIO[Clock, *]] =
      wsb => routes.map(_.apply(wsb)).reduceK.orNotFound

    val serverResource = BlazeServerBuilder[RIO[Clock, *]]
      .withExecutionContext(ExecutionContext.global)
      .bindHttp(0, "localhost")
      .withHttpWebSocketApp(service)
      .resource
      .map(_.address.getPort)

    // Converting a zio.RIO-resource to an cats.IO-resource
    val runtime = implicitly[zio.Runtime[Clock]]
    Resource
      .eval(IO.fromFuture(IO(runtime.unsafeRunToFuture(serverResource.allocated))))
      .flatMap { case (port, release) =>
        Resource.make(IO.pure(port))(_ => IO.fromFuture(IO(runtime.unsafeRunToFuture(release))))
      }
  }
}
