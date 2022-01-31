package sttp.tapir.server.ziohttp

import cats.data.NonEmptyList
import sttp.client3._
import sttp.model.StatusCode
import sttp.tapir.server.tests.CreateServerTest
import sttp.tapir.ztapir._
import zhttp.http._
import zio.{Task, ZIO}
import org.scalatest.matchers.should.Matchers._

class ZioHttpCompositionTest(
    createServerTest: CreateServerTest[Task, Any, Http[Any, Throwable, zhttp.http.Request, zhttp.http.Response]]
) {
  import createServerTest._

  def tests() = List(
    testServer(
      "zio http apps compose after creation", {
        val ep1 = endpoint.get.in("p1").zServerLogic[Any](_ => ZIO.unit)
        val ep3 = endpoint.get.in("p3").zServerLogic[Any](_ => ZIO.fail(new RuntimeException("boom")))

        val route1: RHttpApp[Any] = ZioHttpInterpreter().toHttp(ep1)
        val route2: RHttpApp[Any] = Http.collect { case Method.GET -> Root / "p2" =>
          zhttp.http.Response.ok
        }
        val route3: RHttpApp[Any] = ZioHttpInterpreter().toHttp(ep3)

        NonEmptyList.of(route3, route1, route2)
      }
    ) { (backend, baseUri) =>
      basicRequest.get(uri"$baseUri/p1").send(backend).map(_.code shouldBe StatusCode.Ok) >>
        basicRequest.get(uri"$baseUri/p2").send(backend).map(_.code shouldBe StatusCode.Ok) >>
        basicRequest.get(uri"$baseUri/p3").send(backend).map(_.code shouldBe StatusCode.BadRequest)
    }
  )
}
