package sttp.tapir.server.vertx

import cats.data.NonEmptyList
import cats.effect.std.Dispatcher
import cats.effect.{IO, Resource}
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServerOptions
import io.vertx.ext.web.{Route, Router}
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureHandler, DefaultDecodeFailureHandler}
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.server.vertx.VertxCatsServerInterpreter.CatsFFromVFuture
import sttp.tapir.tests.Port

class CatsVertxTestServerInterpreter(vertx: Vertx, dispatcher: Dispatcher[IO])
    extends TestServerInterpreter[IO, Fs2Streams[IO], Router => Route] {

  private val ioFromVFuture = new CatsFFromVFuture[IO]

  override def route(
      e: ServerEndpoint[Fs2Streams[IO], IO],
      decodeFailureHandler: Option[DecodeFailureHandler],
      metricsInterceptor: Option[MetricsRequestInterceptor[IO]] = None
  ): Router => Route = {
    val options: VertxCatsServerOptions[IO] =
      VertxCatsServerOptions
        .customInterceptors[IO](dispatcher)
        .metricsInterceptor(metricsInterceptor)
        .decodeFailureHandler(decodeFailureHandler.getOrElse(DefaultDecodeFailureHandler.default))
        .options
    VertxCatsServerInterpreter(options).route(e)
  }

  override def route(es: List[ServerEndpoint[Fs2Streams[IO], IO]]): Router => Route = ???

  override def server(routes: NonEmptyList[Router => Route]): Resource[IO, Port] = {
    val router = Router.router(vertx)
    routes.toList.foreach(_.apply(router))
    val server = vertx.createHttpServer(new HttpServerOptions().setPort(0)).requestHandler(router)
    val listenIO = ioFromVFuture(server.listen(0))
    Resource.make(listenIO)(s => ioFromVFuture(s.close).void).map(_.actualPort())
  }
}
