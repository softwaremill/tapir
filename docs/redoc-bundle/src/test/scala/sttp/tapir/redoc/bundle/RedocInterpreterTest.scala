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

  test(s"redoc UI under root") {
    redocTest(Nil, Nil).unsafeRunSync()
  }

  test(s"redoc UI under /api/v1 and empty endpoint") {
    redocTest(Nil, List("api", "v1")).unsafeRunSync()
  }

  test(s"redoc UI under / route and /docs/ endpoint") {
    redocTest(List("docs"), Nil).unsafeRunSync()
  }

  test(s"redoc UI under /internal route /docs/ endpoint") {
    redocTest(List("docs"), List("internal")).unsafeRunSync()
  }

  test(s"redoc UI under /internal/secret route /api/docs/ endpoint ") {
    redocTest(List("api", "docs"), List("internal", "secret")).unsafeRunSync()
  }

  private def redocTest(prefix: List[String], context: List[String]): IO[Assertion] = {
    val redocUIRoutes: HttpRoutes[IO] =
      Http4sServerInterpreter[IO]().toRoutes(
        RedocInterpreter(redocUIOptions = RedocUIOptions.default.copy(pathPrefix = prefix, contextPath = context))
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

          // test redirect from no-trailing-slash, which should return index.html in the end
          val resp: Response[String] = basicRequest
            .response(asStringAlways)
            .get(uri"http://localhost:$port/${context ++ prefix}/")
            .send(client)

          val docsPath = (context ++ prefix).mkString("/")

          val docPathWithTrail = if (docsPath.isEmpty) docsPath else docsPath + "/"

          resp.code shouldBe StatusCode.Ok
          resp.history.head.code shouldBe StatusCode.PermanentRedirect
          resp.history.head.headers("Location").head shouldBe s"/$docPathWithTrail${RedocUIOptions.default.htmlName}"
          resp.body should include(s"/${docPathWithTrail}docs.yaml")
        }
      }
  }

}
