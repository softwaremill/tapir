package sttp.tapir.server.vertx

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import io.vertx.core.http.HttpServerOptions
import io.vertx.core.{Vertx, Future => VFuture}
import io.vertx.ext.web.{Route, Router}
import sttp.capabilities.WebSockets
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.server.vertx.streams.VertxStreams
import sttp.tapir.tests._

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class VertxTestServerInterpreter(vertx: Vertx)
    extends TestServerInterpreter[Future, VertxStreams with WebSockets, VertxFutureServerOptions, Router => Route] {
  import VertxTestServerInterpreter._

  override def route(es: List[ServerEndpoint[VertxStreams with WebSockets, Future]], interceptors: Interceptors): Router => Route = {
    router =>
      val options: VertxFutureServerOptions = interceptors(VertxFutureServerOptions.customiseInterceptors).options
      val interpreter = VertxFutureServerInterpreter(options)
      es.map(interpreter.route(_)(router)).last
  }

  override def server(
      routes: NonEmptyList[Router => Route],
      gracefulShutdownTimeout: Option[FiniteDuration]
  ): Resource[IO, Port] = {
    val router = Router.router(vertx)
    val server = vertx.createHttpServer(new HttpServerOptions().setPort(0)).requestHandler(router)
    val listenIO = vertxFutureToIo(server.listen(0))
    routes.toList.foreach(_.apply(router))
    // Vertx doesn't offer graceful shutdown with timeout OOTB
    Resource.make(listenIO)(s => vertxFutureToIo(s.close()).void).map(s => s.actualPort())
  }
}

object VertxTestServerInterpreter {
  def vertxFutureToIo[A](future: => VFuture[A]): IO[A] =
    IO.async[A] { cb =>
      IO {
        future
          .onFailure { cause => cb(Left(cause)) }
          .onSuccess { result => cb(Right(result)) }
        Some(IO.unit)
      }
    }
}
