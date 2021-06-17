package sttp.tapir.server.vertx

import cats.data.NonEmptyList
import cats.effect.{IO, Resource, Sync}
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServerOptions
import io.vertx.ext.web.{Route, Router, RoutingContext}
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.Endpoint
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureHandler, DefaultDecodeFailureHandler}
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.tests.Port

import scala.reflect.ClassTag

class CatsVertxTestServerInterpreter(vertx: Vertx)
    extends TestServerInterpreter[IO, Fs2Streams[IO], Router => Route, RoutingContext => Unit] {
  import VertxCatsServerInterpreter._

  private val ioFromVFuture = new CatsFFromVFuture[IO]

  override def route[I, E, O](
      e: ServerEndpoint[I, E, O, Fs2Streams[IO], IO],
      decodeFailureHandler: Option[DecodeFailureHandler],
      metricsInterceptor: Option[MetricsRequestInterceptor[IO, RoutingContext => Unit]] = None
  ): Router => Route = {
    val options: VertxCatsServerOptions[IO] =
      VertxCatsServerOptions.customInterceptors(
        metricsInterceptor = metricsInterceptor,
        decodeFailureHandler = decodeFailureHandler.getOrElse(DefaultDecodeFailureHandler.handler)
      )
    VertxCatsServerInterpreter(options).route(e)
  }

  override def routeRecoverErrors[I, E <: Throwable, O](e: Endpoint[I, E, O, Fs2Streams[IO]], fn: I => IO[O])(implicit
      eClassTag: ClassTag[E]
  ): Router => Route = {
    val fs: Sync[IO] = Sync[IO]
    VertxCatsServerInterpreter()(fs).routeRecoverErrors(e)(fn)
  }

  override def server(routes: NonEmptyList[Router => Route]): Resource[IO, Port] = {
    val router = Router.router(vertx)
    val server = vertx.createHttpServer(new HttpServerOptions().setPort(0)).requestHandler(router)
    val listenIO = ioFromVFuture(server.listen(0))
    routes.toList.foreach(_.apply(router))
    Resource.make(listenIO)(s => ioFromVFuture(s.close).void).map(_.actualPort())
  }
}
