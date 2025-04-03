package sttp.tapir.server.ziohttp

import cats.data.NonEmptyList
import org.scalactic.source.Position.here
import org.scalatest.matchers.should.Matchers._
import sttp.client4._
import sttp.model.StatusCode
import sttp.tapir.server.tests.CreateServerTest
import sttp.tapir.ztapir._
import zio.http.{Response => ZioHttpResponse, endpoint => _, _}
import zio.{Task, ZIO}

class ZioHttpCompositionTest(
    createServerTest: CreateServerTest[
      Task,
      Any,
      ZioHttpServerOptions[Any],
      Routes[Any, ZioHttpResponse]
    ]
) {
  import createServerTest._

  def tests() = List(
    testServer(
      "zio http apps compose after creation", {
        val ep1 = endpoint.get.in("p1").zServerLogic[Any](_ => ZIO.unit)
        val ep3 = endpoint.get.in("p3").zServerLogic[Any](_ => ZIO.fail(new RuntimeException("boom")))

        val route1: Routes[Any, ZioHttpResponse] = ZioHttpInterpreter().toHttp(ep1)
        val route2: Routes[Any, ZioHttpResponse] = Routes(Method.GET / "p2" -> handler(ZioHttpResponse.ok))
        val route3: Routes[Any, ZioHttpResponse] = ZioHttpInterpreter().toHttp(ep3)

        NonEmptyList.of(route3, route1, route2)
      }
    ) { (backend, baseUri) =>
      basicRequest.get(uri"$baseUri/p1").send(backend).map(_.code shouldBe StatusCode.Ok) >>
        basicRequest.get(uri"$baseUri/p2").send(backend).map(_.code shouldBe StatusCode.Ok) >>
        basicRequest.get(uri"$baseUri/p3").send(backend).map(_.code shouldBe StatusCode.BadRequest)
    }
  )
}
