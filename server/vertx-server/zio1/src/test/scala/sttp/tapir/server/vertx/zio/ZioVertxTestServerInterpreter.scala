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
import sttp.tapir.tests.Port
import _root_.zio.{Has, RIO, Runtime}
import _root_.zio.blocking.Blocking
import sttp.tapir.server.vertx.VertxTestServerInterpreter

class ZioVertxTestServerInterpreter(vertx: Vertx)
    extends TestServerInterpreter[RIO[Blocking, *], ZioStreams with WebSockets, VertxZioServerOptions[RIO[Blocking, *]], Router => Route] {
  import ZioVertxTestServerInterpreter._

  override def route(
      es: List[ServerEndpoint[ZioStreams with WebSockets, RIO[Blocking, *]]],
      interceptors: Interceptors
  ): Router => Route = { router =>
    val options: VertxZioServerOptions[RIO[Blocking, *]] = interceptors(VertxZioServerOptions.customiseInterceptors).options
    val interpreter = VertxZioServerInterpreter(options)
    es.map(interpreter.route(_)(runtime)(router)).last
  }

  override def server(routes: NonEmptyList[Router => Route]): Resource[IO, Port] = {
    val router = Router.router(vertx)
    val server = vertx.createHttpServer(new HttpServerOptions().setPort(0)).requestHandler(router)
    routes.toList.foreach(_.apply(router))
    Resource.eval(VertxTestServerInterpreter.vertxFutureToIo(server.listen(0)).map(_.actualPort()))
  }
}

object ZioVertxTestServerInterpreter {
  implicit val runtime: Runtime[Blocking] = Runtime.default.as(Has(Blocking.Service.live))
}
