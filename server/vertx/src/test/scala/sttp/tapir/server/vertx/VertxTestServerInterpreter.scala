package sttp.tapir.server.vertx

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import io.vertx.core.http.HttpServerOptions
import io.vertx.core.{Vertx, Future => VFuture}
import io.vertx.ext.web.{Route, Router, RoutingContext}
import sttp.tapir.Endpoint
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureHandler, DefaultDecodeFailureHandler}
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.tests.Port

import scala.concurrent.Future
import scala.reflect.ClassTag

class VertxTestServerInterpreter(vertx: Vertx) extends TestServerInterpreter[Future, Any, Router => Route] {
  import VertxTestServerInterpreter._

  override def route(
      e: ServerEndpoint[Any, Future],
      decodeFailureHandler: Option[DecodeFailureHandler],
      metricsInterceptor: Option[MetricsRequestInterceptor[Future]] = None
  ): Router => Route = {
    val options: VertxFutureServerOptions = VertxFutureServerOptions.customInterceptors
      .metricsInterceptor(metricsInterceptor)
      .decodeFailureHandler(decodeFailureHandler.getOrElse(DefaultDecodeFailureHandler.default))
      .options
    VertxFutureServerInterpreter(options).route(e)
  }

  override def route(es: List[ServerEndpoint[Any, Future]]): Router => Route = router => es.map(route(_)(router)).last

  override def server(routes: NonEmptyList[Router => Route]): Resource[IO, Port] = {
    val router = Router.router(vertx)
    val server = vertx.createHttpServer(new HttpServerOptions().setPort(0)).requestHandler(router)
    val listenIO = vertxFutureToIo(server.listen(0))
    routes.toList.foreach(_.apply(router))
    Resource.make(listenIO)(s => vertxFutureToIo(s.close()).void).map(_.actualPort())
  }
}

object VertxTestServerInterpreter {
  def vertxFutureToIo[A](future: => VFuture[A]): IO[A] =
    IO.async_[A] { cb =>
      future
        .onFailure { cause => cb(Left(cause)) }
        .onSuccess { result => cb(Right(result)) }
      ()
    }
}
