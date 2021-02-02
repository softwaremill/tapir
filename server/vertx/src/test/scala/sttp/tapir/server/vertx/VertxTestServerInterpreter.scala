package sttp.tapir.server.vertx

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import io.vertx.core.{Future => VFuture}
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServerOptions
import io.vertx.ext.web.{Route, Router}
import sttp.tapir.Endpoint
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.server.{DecodeFailureHandler, ServerDefaults, ServerEndpoint}
import sttp.tapir.tests.Port

import scala.reflect.ClassTag
import scala.concurrent.Future

class VertxTestServerInterpreter(vertx: Vertx) extends TestServerInterpreter[Future, Any, Router => Route] {
  import VertxTestServerInterpreter._

  implicit val options: VertxFutureEndpointOptions = VertxFutureEndpointOptions()
    .logWhenHandled(true)
    .logAllDecodeFailures(true)

  override def route[I, E, O](
      e: ServerEndpoint[I, E, O, Any, Future],
      decodeFailureHandler: Option[DecodeFailureHandler]
  ): Router => Route =
    VertxFutureServerInterpreter.route(e)(options.copy(decodeFailureHandler.getOrElse(ServerDefaults.decodeFailureHandler)))

  override def routeRecoverErrors[I, E <: Throwable, O](e: Endpoint[I, E, O, Any], fn: I => Future[O])(implicit
      eClassTag: ClassTag[E]
  ): Router => Route =
    VertxFutureServerInterpreter.routeRecoverErrors(e)(fn)

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
    IO.async[A] { cb =>
      future
        .onFailure { cause => cb(Left(cause)) }
        .onSuccess { result => cb(Right(result)) }
      ()
    }
}
