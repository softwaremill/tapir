package sttp.tapir.server.http4s

import cats.data.{Kleisli, NonEmptyList}
import cats.effect._
import cats.implicits._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.syntax.kleisli._
import org.http4s.{EntityBody, HttpRoutes, Request, Response}
import sttp.tapir.server.tests.ServerTests
import sttp.tapir.Endpoint
import sttp.tapir._
import sttp.client._
import sttp.tapir.server.{DecodeFailureHandler, ServerDefaults}
import sttp.tapir.tests.{Port, PortCounter}

import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

class Http4sServerTests extends ServerTests[IO, EntityBody[IO], HttpRoutes[IO]] {
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  implicit val contextShift: ContextShift[IO] = IO.contextShift(ec)
  implicit val timer: Timer[IO] = IO.timer(ec)

  override def pureResult[T](t: T): IO[T] = IO.pure(t)
  override def suspendResult[T](t: => T): IO[T] = IO.apply(t)

  override def route[I, E, O](
      e: Endpoint[I, E, O, EntityBody[IO]],
      fn: I => IO[Either[E, O]],
      decodeFailureHandler: Option[DecodeFailureHandler] = None
  ): HttpRoutes[IO] = {
    implicit val serverOptions: Http4sServerOptions[IO] = Http4sServerOptions
      .default[IO]
      .copy(
        decodeFailureHandler = decodeFailureHandler.getOrElse(ServerDefaults.decodeFailureHandler)
      )
    e.toRoutes(fn)
  }

  override def routeRecoverErrors[I, E <: Throwable, O](e: Endpoint[I, E, O, EntityBody[IO]], fn: I => IO[O])(
      implicit eClassTag: ClassTag[E]
  ): HttpRoutes[IO] = {
    e.toRouteRecoverErrors(fn)
  }

  override def server(routes: NonEmptyList[HttpRoutes[IO]], port: Port): Resource[IO, Unit] = {
    val service: Kleisli[IO, Request[IO], Response[IO]] = routes.reduceK.orNotFound

    BlazeServerBuilder[IO]
      .bindHttp(port, "localhost")
      .withHttpApp(service)
      .resource
      .void
  }

  override lazy val portCounter: PortCounter = new PortCounter(59000)

  if (testNameFilter.isEmpty) {
    test("should work with a router and routes in a context") {
      val e = endpoint.get.in("test" / "router").out(stringBody).serverLogic(_ => IO.pure("ok".asRight[Unit]))
      val routes = e.toRoutes
      val port = portCounter.next()

      BlazeServerBuilder[IO]
        .bindHttp(port, "localhost")
        .withHttpApp(Router("/api" -> routes).orNotFound)
        .resource
        .use { _ =>
          basicRequest.get(uri"http://localhost:$port/api/test/router").send().map(_.body shouldBe Right("ok"))
        }
        .unsafeRunSync()
    }
  }
}
