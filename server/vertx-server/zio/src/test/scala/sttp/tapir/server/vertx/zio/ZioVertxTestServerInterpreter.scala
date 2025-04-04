package sttp.tapir.server.vertx.zio

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServerOptions
import io.vertx.ext.web.{Route, Router}
import sttp.capabilities.zio.ZioStreams
import sttp.capabilities.WebSockets
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.tests._
import _root_.zio.{Runtime, Task}
import sttp.tapir.server.vertx.VertxTestServerInterpreter
import scala.concurrent.duration.FiniteDuration

class ZioVertxTestServerInterpreter(vertx: Vertx)
    extends TestServerInterpreter[Task, ZioStreams with WebSockets, VertxZioServerOptions[Any], Router => Route] {
  import ZioVertxTestServerInterpreter._

  override def route(es: List[ServerEndpoint[ZioStreams with WebSockets, Task]], interceptors: Interceptors): Router => Route = { router =>
    val options: VertxZioServerOptions[Any] = interceptors(VertxZioServerOptions.customiseInterceptors).options
    val interpreter = VertxZioServerInterpreter(options)
    es.map(interpreter.route(_)(runtime)(router)).last
  }

  override def server(
      routes: NonEmptyList[Router => Route],
      gracefulShutdownTimeout: Option[FiniteDuration]
  ): Resource[IO, Port] = {
    val router = Router.router(vertx)
    // the maxFormAttributeSize must be higher than in ServerMultipartTests.maxContentLengthTests
    val server = vertx.createHttpServer(new HttpServerOptions().setPort(0).setMaxFormAttributeSize(100000)).requestHandler(router)
    routes.toList.foreach(_.apply(router))

    val listenIO = VertxTestServerInterpreter.vertxFutureToIo(server.listen(0))
    // Vertx doesn't offer graceful shutdown with timeout OOTB
    Resource.make(listenIO)(server => VertxTestServerInterpreter.vertxFutureToIo(server.close).void).map(_.actualPort())
  }
}

object ZioVertxTestServerInterpreter {
  implicit val runtime: Runtime[Any] = Runtime.default
}
