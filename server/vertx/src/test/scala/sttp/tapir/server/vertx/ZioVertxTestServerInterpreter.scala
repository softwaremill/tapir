package sttp.tapir.server.vertx

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServerOptions
import io.vertx.ext.web.{Route, Router}
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.tests.Port
import zio.{Runtime, Task}

class ZioVertxTestServerInterpreter(vertx: Vertx)
    extends TestServerInterpreter[Task, ZioStreams, VertxZioServerOptions[Task], Router => Route] {
  import ZioVertxTestServerInterpreter._

  override def route(es: List[ServerEndpoint[ZioStreams, Task]], interceptors: Interceptors): Router => Route = {
    val options: VertxZioServerOptions[Task] = interceptors(VertxZioServerOptions.customInterceptors).options
    val interpreter = VertxZioServerInterpreter(options)
    es.map(interpreter.route).last
  }

  override def server(routes: NonEmptyList[Router => Route]): Resource[IO, Port] = {
    val router = Router.router(vertx)
    val server = vertx.createHttpServer(new HttpServerOptions().setPort(0)).requestHandler(router)
    routes.toList.foreach(_.apply(router))
    Resource.eval(VertxTestServerInterpreter.vertxFutureToIo(server.listen(0)).map(_.actualPort()))
  }
}

object ZioVertxTestServerInterpreter {
  implicit val runtime: Runtime[zio.ZEnv] = Runtime.default
}
