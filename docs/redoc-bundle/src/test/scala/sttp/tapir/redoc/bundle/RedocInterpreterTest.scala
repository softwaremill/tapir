package sttp.tapir.redoc.bundle

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.http4s.HttpRoutes
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import org.scalatest.Assertion
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.client3.{Identity => _, _} // tapir has its own Identity type alias
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.redoc.RedocUIOptions
import sttp.tapir.server.http4s.Http4sServerInterpreter

class RedocInterpreterTest extends AsyncFunSuite with Matchers {

  val testEndpoint: PublicEndpoint[String, Unit, String, Any] = endpoint.get
    .in("test")
    .in(query[String]("q"))
    .out(stringBody)

  test(s"redoc UI at root, relative path") {
    redocTest(Nil, Nil, useRelativePaths = true).unsafeRunSync()
  }

  test(s"redoc UI at ./docs endpoint, relative path") {
    redocTest(List("docs"), Nil, useRelativePaths = true).unsafeRunSync()
  }

  test("redoc UI at ./api/docs endpoint, relative path") {
    redocTest(List("api", "docs"), Nil, useRelativePaths = true).unsafeRunSync()
  }

  test(s"redoc UI at root, absolute path") {
    redocTest(Nil, Nil, useRelativePaths = false).unsafeRunSync()
  }

  test(s"redoc UI at ./docs endpoint, absolute path") {
    redocTest(List("docs"), Nil, useRelativePaths = false).unsafeRunSync()
  }

  test("redoc UI at ./api/docs endpoint, absolute path") {
    redocTest(List("api", "docs"), Nil, useRelativePaths = false).unsafeRunSync()
  }

  test(s"redoc UI at /api/v1 and empty endpoint, absolute path") {
    redocTest(Nil, List("api", "v1"), useRelativePaths = false).unsafeRunSync()
  }

  test(s"redoc UI at /internal route /docs endpoint, absolute path") {
    redocTest(List("docs"), List("internal"), useRelativePaths = false).unsafeRunSync()
  }

  test(s"redoc UI at /internal/secret route /api/docs endpoint, absolute path") {
    redocTest(List("api", "docs"), List("internal", "secret"), useRelativePaths = false).unsafeRunSync()
  }

  private def redocTest(prefix: List[String], context: List[String], useRelativePaths: Boolean): IO[Assertion] = {
    val redocUIRoutes: HttpRoutes[IO] =
      Http4sServerInterpreter[IO]().toRoutes(
        RedocInterpreter(redocUIOptions =
          RedocUIOptions.default.copy(pathPrefix = prefix, contextPath = context, useRelativePaths = useRelativePaths)
        )
          .fromEndpoints[IO](List(testEndpoint), "The tapir library", "1.0.0")
      )

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
            if (useRelativePaths) "."
            else (if ((context ++ prefix).isEmpty) ""
                  else s"/${(context ++ prefix).mkString("/")}")
          resp.body should include(s"$docPath/docs.yaml")

          val redirectPath = if (useRelativePaths) {
            prefix.lastOption match {
              case Some(lastPrefix) => s"./$lastPrefix"
              case None             => "."
            }
          } else {
            (context ++ prefix) match {
              case Nil => ""
              case l   => "/" + l.mkString("/")
            }
          }
          val firstHistory = resp.history.head
          firstHistory.code shouldBe StatusCode.PermanentRedirect
          println(firstHistory.header("Location"))
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
