package sttp.tapir.server.http4s

import cats.effect.IO
import cats.implicits._
import org.http4s.HttpRoutes
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.syntax.kleisli._
import sttp.client3._
import sttp.tapir._
import sttp.tapir.server.tests.ServerBasicTests
import sttp.tapir.tests.PortCounter

import scala.concurrent.ExecutionContext

class Http4sServerBasicTests extends Http4sServerTests[Any] with ServerBasicTests[IO, HttpRoutes[IO]] {
  basicTests()

  if (testNameFilter.isEmpty) {
    test("should work with a router and routes in a context") {
      val e = endpoint.get.in("test" / "router").out(stringBody).serverLogic(_ => IO.pure("ok".asRight[Unit]))
      val routes = e.toRoutes
      val port = portCounter.next()

      BlazeServerBuilder[IO](ExecutionContext.global)
        .bindHttp(port, "localhost")
        .withHttpApp(Router("/api" -> routes).orNotFound)
        .resource
        .use { _ => basicRequest.get(uri"http://localhost:$port/api/test/router").send(backend).map(_.body shouldBe Right("ok")) }
        .unsafeRunSync()
    }
  }

  override val portCounter: PortCounter = new PortCounter(31000)
}
