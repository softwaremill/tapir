package sttp.tapir.redoc.bundle

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.http4s.HttpRoutes
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import org.scalatest.Assertion
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.client3._
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.redoc.RedocUIOptions
import sttp.tapir.server.http4s.Http4sServerInterpreter

class RedocInterpreterTest extends AsyncFunSuite with Matchers {

  val testEndpoint: PublicEndpoint[String, Unit, String, Any] = endpoint.get
    .in("test")
    .in(query[String]("q"))
    .out(stringBody)

  test(s"redoc UI at root") {
    redocTest(Nil, Nil).unsafeRunSync()
  }

  test(s"redoc UI at ./docs endpoint") {
    redocTest(List("docs"), Nil).unsafeRunSync()
  }

  test("redoc UI at ./api/docs endpoint") {
    redocTest(List("api", "docs"), Nil).unsafeRunSync()
  }

  test(s"redoc UI at /api/v1 and empty endpoint") {
    redocTest(Nil, List("api", "v1")).unsafeRunSync()
  }

  test(s"redoc UI at /internal route /docs endpoint") {
    redocTest(List("docs"), List("internal")).unsafeRunSync()
  }

  test(s"redoc UI at /internal/secret route /api/docs endpoint ") {
    redocTest(List("api", "docs"), List("internal", "secret")).unsafeRunSync()
  }

  private def redocTest(prefix: List[String], context: List[String]): IO[Assertion] = {
    val redocUIRoutes: HttpRoutes[IO] =
      Http4sServerInterpreter[IO]().toRoutes(
        RedocInterpreter(redocUIOptions = RedocUIOptions.default.copy(pathPrefix = prefix, contextPath = context))
          .fromEndpoints[IO](List(testEndpoint), "The tapir library", "1.0.0")
      )
    val useRelativePath = context.isEmpty

    BlazeServerBuilder[IO]
      .bindHttp(0, "localhost")
      .withHttpApp(Router(s"/${context.mkString("/")}" -> redocUIRoutes).orNotFound)
      .resource
      .use { server =>
        IO {
          val port = server.address.getPort
          val client: SttpBackend[Identity, Any] = HttpURLConnectionBackend()

          // test no-trailing-slash redirect to index.html
          val resp: Response[String] = basicRequest
            .response(asStringAlways)
            .get(uri"http://localhost:$port/${context ++ prefix}")
            .send(client)

          resp.code shouldBe StatusCode.Ok
          resp.history should have size (1)

          val docPath =
            if (useRelativePath) "."
            else (if ((context ++ prefix).isEmpty) ""
                  else s"/${(context ++ prefix).mkString("/")}")
          resp.body should include(s"$docPath/docs.yaml")

          val redirectPath = if (useRelativePath) {
            prefix.lastOption match {
              case Some(lastPrefix) => s"./$lastPrefix"
              case None             => "."
            }
          } else "/" + (context ++ prefix).mkString("/")
          val firstHistory = resp.history.head
          firstHistory.code shouldBe StatusCode.PermanentRedirect
          firstHistory.header("Location") shouldBe Some(s"$redirectPath/${RedocUIOptions.default.htmlName}")

          // test getting docs.yaml
          val yamlResp: Response[String] = basicRequest
            .response(asStringAlways)
            .get(uri"http://localhost:$port/${context ++ prefix}/docs.yaml")
            .send(client)

          yamlResp.code shouldBe StatusCode.Ok
          yamlResp.body should include("The tapir library")
        }
      }
  }

}
