package sttp.tapir.server.vertx.cats

import cats.data.NonEmptyList
import cats.effect.std.Dispatcher
import cats.effect.{IO, Resource}
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServerOptions
import io.vertx.ext.web.{Route, Router}
import sttp.capabilities.fs2.Fs2Streams
import sttp.capabilities.WebSockets
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.server.vertx.cats.VertxCatsServerInterpreter.CatsFFromVFuture
import sttp.tapir.tests._

import scala.concurrent.duration.FiniteDuration

class CatsVertxTestServerInterpreter(vertx: Vertx, dispatcher: Dispatcher[IO])
    extends TestServerInterpreter[IO, Fs2Streams[IO] with WebSockets, VertxCatsServerOptions[IO], Router => Route] {

  private val ioFromVFuture = new CatsFFromVFuture[IO]

  override def route(es: List[ServerEndpoint[Fs2Streams[IO] with WebSockets, IO]], interceptors: Interceptors): Router => Route = {
    router =>
      val options: VertxCatsServerOptions[IO] = interceptors(VertxCatsServerOptions.customiseInterceptors[IO](dispatcher)).options
      val interpreter = VertxCatsServerInterpreter(options)
      es.map(interpreter.route(_)(router)).last
  }

  override def server(
      routes: NonEmptyList[Router => Route],
      gracefulShutdownTimeout: Option[FiniteDuration]
  ): Resource[IO, Port] = {
    val router = Router.router(vertx)
    routes.toList.foreach(_.apply(router))
    val server = vertx.createHttpServer(new HttpServerOptions().setPort(0)).requestHandler(router)
    val listenIO = ioFromVFuture(server.listen(0))
    // Vertx doesn't offer graceful shutdown with timeout OOTB
    Resource.make(listenIO)(s => ioFromVFuture(s.close).void).map(_.actualPort())
  }
}
