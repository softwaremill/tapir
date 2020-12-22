package sttp.tapir.server.vertx

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import io.vertx.scala.core.Vertx
import io.vertx.scala.core.http.HttpServerOptions
import io.vertx.scala.ext.web.{Route, Router}
import sttp.tapir.Endpoint
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.server.{DecodeFailureHandler, ServerDefaults, ServerEndpoint}
import sttp.tapir.tests.Port

import scala.concurrent.Future
import scala.reflect.ClassTag

class VertxTestServerInterpreter(vertx: Vertx) extends TestServerInterpreter[Future, Any, Router => Route] {
  implicit val options: VertxEndpointOptions = VertxEndpointOptions()
    .logWhenHandled(true)
    .logAllDecodeFailures(true)

  override def route[I, E, O](
      e: ServerEndpoint[I, E, O, Any, Future],
      decodeFailureHandler: Option[DecodeFailureHandler]
  ): Router => Route =
    VertxServerInterpreter.route(e)(options.copy(decodeFailureHandler.getOrElse(ServerDefaults.decodeFailureHandler)))

  override def routeRecoverErrors[I, E <: Throwable, O](e: Endpoint[I, E, O, Any], fn: I => Future[O])(implicit
      eClassTag: ClassTag[E]
  ): Router => Route =
    VertxServerInterpreter.routeRecoverErrors(e)(fn)

  override def server(routes: NonEmptyList[Router => Route]): Resource[IO, Port] = {
    val router = Router.router(vertx)
    val server = vertx.createHttpServer(HttpServerOptions().setPort(0)).requestHandler(router)
    val listenIO = IO.fromFuture(IO(server.listenFuture(0)))
    routes.toList.foreach(_.apply(router))
    Resource.make(listenIO)(s => IO.fromFuture(IO(s.closeFuture()))).map(_.actualPort())
  }
}
