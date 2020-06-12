package sttp.tapir.server.vertx

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import cats.implicits._
import io.vertx.lang.scala.VertxExecutionContext
import io.vertx.scala.core.Vertx
import io.vertx.scala.core.http.HttpServerOptions
import io.vertx.scala.ext.web.{Route, Router}
import org.scalatest.BeforeAndAfterEach
import sttp.tapir._
import sttp.tapir.server.tests.ServerTests
import sttp.tapir.server.{DecodeFailureHandler, ServerDefaults, ServerEndpoint}
import sttp.tapir.tests.{Port, PortCounter}

import scala.concurrent.Future
import scala.reflect.ClassTag

class VertxServerTests extends ServerTests[Future, String, Router => Route] with BeforeAndAfterEach {

  implicit val options: VertxEndpointOptions = VertxEndpointOptions()
    .logWhenHandled(true)
    .logAllDecodeFailures(true)

  override def multipartInlineHeaderSupport: Boolean = false // README: doesn't seem supported but I may be wrong

  protected var vertx: Vertx = _

  override def beforeAll(): Unit = {
    super.beforeAll()
    vertx = Vertx.vertx()
  }

  override def afterAll(): Unit = {
    super.afterAll()
    vertx.close()
  }

  override def pureResult[T](t: T): Future[T] = Future.successful(t)
  override def suspendResult[T](t: => T): Future[T] = Future(t)(VertxExecutionContext(vertx.getOrCreateContext()))

  override def route[I, E, O](
      e: ServerEndpoint[I, E, O, String, Future],
      decodeFailureHandler: Option[DecodeFailureHandler]
  ): Router => Route =
    e.route(options.copy(decodeFailureHandler.getOrElse(ServerDefaults.decodeFailureHandler)))

  override def routeRecoverErrors[I, E <: Throwable, O](e: Endpoint[I, E, O, String], fn: I => Future[O])(implicit
      eClassTag: ClassTag[E]
  ): Router => Route =
    e.routeRecoverErrors(fn)

  override def server(routes: NonEmptyList[Router => Route], port: Port): Resource[IO, Unit] = {
    val router = Router.router(vertx)
    val server = vertx.createHttpServer(HttpServerOptions().setPort(port)).requestHandler(router)
    val listenIO = IO.fromFuture(IO(server.listenFuture(port)))
    routes.toList.foreach(_.apply(router))
    Resource.make(listenIO)(s => IO(s.closeFuture())).void
  }

  override lazy val portCounter: PortCounter = new PortCounter(54000)

}
