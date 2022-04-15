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
import sttp.tapir.swagger.SwaggerUIOptions

class SwaggerInterpreterTest extends AsyncFunSuite with Matchers {

  case class Author(name: String)
  case class Book(title: String, year: Int, author: Author)

  val testEndpoint: PublicEndpoint[String, Unit, String, Any] = endpoint.get
    .in("test")
    .in(query[String]("q"))
    .out(stringBody)

  def swaggerUITest(prefix: List[String], context: List[String], useRelativePath: Boolean): IO[Assertion] = {
    val swaggerUIRoutes: HttpRoutes[IO] =
      Http4sServerInterpreter[IO]().toRoutes(
        SwaggerInterpreter(swaggerUIOptions =
          SwaggerUIOptions.default.copy(pathPrefix = prefix, contextPath = context, useRelativePath = useRelativePath)
        )
          .fromEndpoints[IO](List(testEndpoint), "The tapir library", "1.0.0")
      )

    BlazeServerBuilder[IO]
      .bindHttp(0, "localhost")
      .withHttpApp(Router(s"/${context.mkString("/")}" -> swaggerUIRoutes).orNotFound)
      .resource
      .use { server =>
        IO {
          val port = server.address.getPort
          val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()

          // test redirect from no-trailing-slash, which should return index.html in the end
          val resp: Response[String] = basicRequest
            .response(asStringAlways)
            .get(uri"http://localhost:$port/${context ++ prefix}")
            .send(backend)

          val docsPath = (context ++ prefix).mkString("/")

          val docPathWithTrail = if (docsPath.isEmpty) docsPath else docsPath + "/"

          resp.code shouldBe StatusCode.Ok
          } else {
            resp.history.headOption.map { historicalResp =>
              historicalResp.code shouldBe StatusCode.PermanentRedirect
              if (useRelativePath) {
                historicalResp.headers("Location").head shouldBe s"./${prefix.last}/"
              } else {
                historicalResp.headers("Location").head shouldBe s"/$docsPath/"
              }
            }
          }

          // test getting swagger-initializer.js, which should contain replaced link to spec
          val initializerJsResp = basicRequest
            .response(asStringAlways)
            .get(uri"http://localhost:$port/${context ++ prefix}/swagger-initializer.js")
            .send(backend)

          if (useRelativePath) {
            initializerJsResp.body should include(s"./docs.yaml")
          } else {
            initializerJsResp.body should include(s"/$docPathWithTrail/docs.yaml")
          }

          // test getting a swagger-ui resource
          val respCss: Response[String] = basicRequest
            .response(asStringAlways)
            .get(uri"http://localhost:$port/${context ++ prefix}/swagger-ui.css")
            .send(backend)

          respCss.code shouldBe StatusCode.Ok
          respCss.body should include(".swagger-ui")
        }
      }
  }



  test("swagger UI at /docs endpoint, using relative path") {
    swaggerUITest(List("docs"), Nil, true).unsafeRunSync()
  }

  test("swagger UI at /api/docs endpoint, using relative path") {
    swaggerUITest(List("api", "docs"), Nil, true).unsafeRunSync()
  }

  test("swagger UI ignores context route using relative path") {
    swaggerUITest(List("api", "docs"), List("ignored"), true).unsafeRunSync()
  }

  test(s"swagger UI under root, no relative path") {
    swaggerUITest(Nil, Nil, false).unsafeRunSync()
  }

  test(s"swagger UI under /api/v1 and empty endpoint, no relative path") {
    swaggerUITest(Nil, List("api", "v1"), false).unsafeRunSync()
  }

  test("swagger UI under / route and /docs endpoint, no relative path") {
    swaggerUITest(List("docs"), Nil, false).unsafeRunSync()
  }

  test("swagger UI under /internal route /docs endpoint, no relative path") {
    swaggerUITest(List("docs"), List("internal"), false).unsafeRunSync()
  }

  test("swagger UI under /internal/secret route /api/docs endpoint, no relative path") {
    swaggerUITest(List("api", "docs"), List("internal", "secret"), false).unsafeRunSync()
  }

}
