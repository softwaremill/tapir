package sttp.tapir.server.vertx

import cats.arrow.FunctionK
import cats.data.NonEmptyList
import cats.effect.{ConcurrentEffect, IO, Resource}
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServerOptions
import io.vertx.ext.web.{Route, Router}
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.Endpoint
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureHandler, DefaultDecodeFailureHandler}
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.tests.Port
import zio.{Runtime, Task}
import zio.interop.catz._

import scala.reflect.ClassTag

class ZioVertxTestServerInterpreter(vertx: Vertx) extends TestServerInterpreter[Task, ZioStreams, Router => Route] {
  import VertxZioServerInterpreter._
  import ZioVertxTestServerInterpreter._

  private val taskFromVFuture = new RioFromVFuture[Any]

  override def route(
      e: ServerEndpoint[ZioStreams, Task],
      decodeFailureHandler: Option[DecodeFailureHandler]
  ): Router => Route = {
    implicit val options: VertxZioServerOptions[Task] =
      VertxZioServerOptions.customInterceptors(decodeFailureHandler = decodeFailureHandler.getOrElse(DefaultDecodeFailureHandler.handler))
    VertxZioServerInterpreter.route(e)
  }

  override def routeRecoverErrors[I, E <: Throwable, O](e: Endpoint[I, E, O, ZioStreams], fn: I => Task[O])(implicit
      eClassTag: ClassTag[E]
  ): Router => Route =
    VertxZioServerInterpreter.routeRecoverErrors(e)(fn)

  override def server(routes: NonEmptyList[Router => Route]): Resource[IO, Port] = {
    val router = Router.router(vertx)
    val server = vertx.createHttpServer(new HttpServerOptions().setPort(0)).requestHandler(router)
    val listenIO = taskFromVFuture(server.listen(0))
    routes.toList.foreach(_.apply(router))
    Resource.make(listenIO)(s => taskFromVFuture(s.close).unit).map(_.actualPort()).mapK(zioToIo)
  }
}

object ZioVertxTestServerInterpreter {
  implicit val runtime: Runtime[zio.ZEnv] = Runtime.default

  val zioToIo: FunctionK[Task, IO] = new FunctionK[Task, IO] {
    override def apply[A](fa: Task[A]): IO[A] =
      ConcurrentEffect[Task].toIO(fa)
  }
}
