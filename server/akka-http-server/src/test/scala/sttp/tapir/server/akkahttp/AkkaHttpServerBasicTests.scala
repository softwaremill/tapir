package sttp.tapir.server.akkahttp

import akka.http.scaladsl.server.{Directives, Route}
import cats.data.NonEmptyList
import sttp.client3._
import sttp.tapir.server.tests.ServerBasicTests
import sttp.tapir._
import cats.implicits._
import sttp.tapir.tests.PortCounter

import scala.concurrent.Future

class AkkaHttpServerBasicTests extends AkkaHttpServerTests[Any] with ServerBasicTests[Future, Route] {
  basicTests()

  if (testNameFilter.isEmpty) {
    test("endpoint nested in a path directive") {
      val e = endpoint.get.in("test" and "directive").out(stringBody).serverLogic(_ => pureResult("ok".asRight[Unit]))
      val port = PortCounter.next()
      val route = Directives.pathPrefix("api")(e.toRoute)
      server(NonEmptyList.of(route), port).use { _ =>
        basicRequest.get(uri"http://localhost:$port/api/test/directive").send(backend).map(_.body shouldBe Right("ok"))
      }.unsafeRunSync
    }
  }
}
