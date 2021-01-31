package sttp.tapir.server.vertx

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServerOptions
import io.vertx.ext.web.{Route, Router}
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.Endpoint
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.server.{DecodeFailureHandler, ServerDefaults, ServerEndpoint}
import sttp.tapir.tests.Port

import scala.reflect.ClassTag

class CatsVertxTestServerInterpreter(vertx: Vertx) extends TestServerInterpreter[IO, Fs2Streams[IO], Router => Route] {
  import VertxCatsServerInterpreter._

  implicit val options = VertxEffectfulEndpointOptions()
    .logWhenHandled(true)
    .logAllDecodeFailures(true)

  override def route[I, E, O](
      e: ServerEndpoint[I, E, O, Fs2Streams[IO], IO],
      decodeFailureHandler: Option[DecodeFailureHandler]
  ): Router => Route =
    VertxCatsServerInterpreter.route(e)(options.copy(decodeFailureHandler.getOrElse(ServerDefaults.decodeFailureHandler)), implicitly)

  override def routeRecoverErrors[I, E <: Throwable, O](e: Endpoint[I, E, O, Fs2Streams[IO]], fn: I => IO[O])(implicit
      eClassTag: ClassTag[E]
  ): Router => Route =
    VertxCatsServerInterpreter.routeRecoverErrors(e)(fn)

  override def server(routes: NonEmptyList[Router => Route]): Resource[IO, Port] = {
    val router = Router.router(vertx)
    val server = vertx.createHttpServer(new HttpServerOptions().setPort(0)).requestHandler(router)
    val listenIO = server.listen(0).liftF[IO]
    routes.toList.foreach(_.apply(router))
    Resource.make(listenIO)(s => s.close.liftF[IO].void).map(_.actualPort())
  }
}
