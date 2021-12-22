package sttp.tapir.swagger.bundle

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
import sttp.tapir.server.http4s.Http4sServerInterpreter

class SwaggerInterpreterTest extends AsyncFunSuite with Matchers {

  case class Author(name: String)
  case class Book(title: String, year: Int, author: Author)

  val testEndpoint: PublicEndpoint[String, Unit, String, Any] = endpoint.get
    .in("test")
    .in(query[String]("q"))
    .out(stringBody)

  def swaggerUITest(prefix: List[String], basePrefix: List[String]): IO[Assertion] = {
    val swaggerUIRoutes: HttpRoutes[IO] =
      Http4sServerInterpreter[IO]().toRoutes(
        SwaggerInterpreter(prefix = prefix, basePrefix = basePrefix)
          .fromEndpoints[IO](List(testEndpoint), "The tapir library", "1.0.0")
      )

    BlazeServerBuilder[IO]
      .bindHttp(0, "localhost")
      .withHttpApp(Router(s"/${basePrefix.mkString("/")}" -> swaggerUIRoutes).orNotFound)
      .resource
      .use { server =>
        IO {
          val port = server.address.getPort
          val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()
          val resp: Response[String] = basicRequest
            .response(asStringAlways)
            .get(uri"http://localhost:$port/${basePrefix ++ prefix}")
            .send(backend)

          val docsPath = (basePrefix ++ prefix).mkString("/")

          resp.code shouldBe StatusCode.Ok
          resp.body should include(s"/$docsPath/docs.yaml")

          resp.history.head.code shouldBe StatusCode.PermanentRedirect
          resp.history.head.headers("Location").head shouldBe s"/$docsPath/"
        }
      }
  }

  test("swagger UI under / route and /docs endpoint") {
    swaggerUITest(List("docs"), Nil).unsafeRunSync()
  }

  test("swagger UI under /internal route /docs endpoint") {
    swaggerUITest(List("docs"), List("internal")).unsafeRunSync()
  }

  test("swagger UI under /internal/secret route /api/docs endpoint") {
    swaggerUITest(List("api", "docs"), List("internal", "secret")).unsafeRunSync()
  }
}
