package sttp.tapir.server.ziohttp

import cats.data.NonEmptyList
import sttp.client3._
import sttp.model.StatusCode
import sttp.tapir.server.tests.CreateServerTest
import sttp.tapir.ztapir._
import zhttp.http._
import zio.{Task, ZIO}
import org.scalatest.matchers.should.Matchers._
import sttp.tapir.tests.Test
import org.scalactic.source.Position.here
import zio.UIO
import org.scalatest.compatible.Assertion
import io.netty.util.CharsetUtil

class ZioHttpCompositionTest(
    createServerTest: CreateServerTest[Task, Any, ZioHttpServerOptions[Any], Http[Any, Throwable, zhttp.http.Request, zhttp.http.Response]]
) {
  import createServerTest._

  def tests() = List(
    testServer(
      "zio http apps compose after creation", {
        val ep1 = endpoint.get.in("p1").zServerLogic[Any](_ => ZIO.unit)
        val ep3 = endpoint.get.in("p3").zServerLogic[Any](_ => ZIO.fail(new RuntimeException("boom")))

        val route1: RHttpApp[Any] = ZioHttpInterpreter().toHttp(ep1)
        val route2: RHttpApp[Any] = Http.collect { case Method.GET -> !! / "p2" =>
          zhttp.http.Response.ok
        }
        val route3: RHttpApp[Any] = ZioHttpInterpreter().toHttp(ep3)

        NonEmptyList.of(route3, route1, route2)
      }
    ) { (backend, baseUri) =>
      basicRequest.get(uri"$baseUri/p1").send(backend).map(_.code shouldBe StatusCode.Ok) >>
        basicRequest.get(uri"$baseUri/p2").send(backend).map(_.code shouldBe StatusCode.Ok) >>
        basicRequest.get(uri"$baseUri/p3").send(backend).map(_.code shouldBe StatusCode.BadRequest)
    },
    new Test(
      "zio http can get a content",
      () => {
        val ep = endpoint.get.in("p1").out(stringBody).zServerLogic[Any](_ => ZIO.succeed("response"))
        val route = ZioHttpInterpreter().toHttp(ep)
        val test: UIO[Assertion] = route(Request(url = URL.apply(Path.apply("p1"))))
          .flatMap(response => response.data.toByteBuf.map(_.toString(CharsetUtil.UTF_8)))
          .map(_ shouldBe "response")
          .catchAll(_ => ZIO.succeed(fail("Unable to extract body from Http response")))
        zio.Runtime.default.unsafeRunToFuture(test)
      },
      here
    )
  )
}
