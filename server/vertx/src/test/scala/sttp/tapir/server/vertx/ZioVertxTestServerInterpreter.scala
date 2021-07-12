package sttp.tapir.server.vertx

import cats.data.NonEmptyList
import cats.effect.std.Dispatcher
import cats.effect.{IO, Resource}
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServerOptions
import io.vertx.ext.web.{Route, Router, RoutingContext}
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.Endpoint
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureHandler, DefaultDecodeFailureHandler}
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.server.vertx.VertxZioServerInterpreter.RioFromVFuture
import sttp.tapir.tests.Port
import zio.{Runtime, Task}

import scala.reflect.ClassTag

class ZioVertxTestServerInterpreter(vertx: Vertx) extends TestServerInterpreter[Task, ZioStreams, Router => Route, RoutingContext => Unit] {
  import ZioVertxTestServerInterpreter._

  private val taskFromVFuture = new RioFromVFuture[Any]

  override def route[I, E, O](
      e: ServerEndpoint[I, E, O, ZioStreams, Task],
      decodeFailureHandler: Option[DecodeFailureHandler],
      metricsInterceptor: Option[MetricsRequestInterceptor[Task, RoutingContext => Unit]] = None
  ): Router => Route = {
    val options: VertxZioServerOptions[Task] =
      VertxZioServerOptions.customInterceptors(
        metricsInterceptor = metricsInterceptor,
        decodeFailureHandler = decodeFailureHandler.getOrElse(DefaultDecodeFailureHandler.handler)
      )
    VertxZioServerInterpreter(options).route(e)
  }

  override def routeRecoverErrors[I, E <: Throwable, O](e: Endpoint[I, E, O, ZioStreams], fn: I => Task[O])(implicit
      eClassTag: ClassTag[E]
  ): Router => Route =
    VertxZioServerInterpreter().routeRecoverErrors(e)(fn)

  override def server(routes: NonEmptyList[Router => Route]): Resource[IO, Port] = {
    val router = Router.router(vertx)
    val server = vertx.createHttpServer(new HttpServerOptions().setPort(0)).requestHandler(router)
    routes.toList.foreach(_.apply(router))
    Dispatcher[IO].map { dispatcher =>
      dispatcher.unsafeRunSync(VertxTestServerInterpreter.vertxFutureToIo(server.listen(0)).map(_.actualPort()))
    }
  }
}

object ZioVertxTestServerInterpreter {
  implicit val runtime: Runtime[zio.ZEnv] = Runtime.default
}
