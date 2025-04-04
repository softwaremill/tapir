package sttp.tapir.swagger.bundle

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.http4s.HttpRoutes
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import org.scalatest.Assertion
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.client4._
import sttp.client4.httpclient.HttpClientSyncBackend
import sttp.model.{Header, MediaType, StatusCode}
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

  def swaggerUITest(prefix: List[String], context: List[String], useRelativePaths: Boolean): IO[Assertion] = {
    val swaggerUIRoutes: HttpRoutes[IO] =
      Http4sServerInterpreter[IO]().toRoutes(
        SwaggerInterpreter(swaggerUIOptions =
          SwaggerUIOptions.default.copy(pathPrefix = prefix, contextPath = context, useRelativePaths = useRelativePaths)
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
          val backend: SyncBackend = HttpClientSyncBackend()

          // test redirect from no-trailing-slash, which should return index.html in the end
          val resp: Response[String] = basicRequest
            .response(asStringAlways)
            .get(uri"http://localhost:$port/${context ++ prefix}")
            .send(backend)

          val docsPath = (context ++ prefix).mkString("/")

          val docPathWithTrail = if (docsPath.isEmpty) docsPath else docsPath + "/"

          resp.code shouldBe StatusCode.Ok
          resp.history.headOption.map { historicalResp =>
            historicalResp.code shouldBe StatusCode.PermanentRedirect
            if (useRelativePaths) {
              historicalResp.headers("Location").head shouldBe s"./${prefix.last}/"
            } else {
              historicalResp.headers("Location").head shouldBe s"/$docsPath/"
            }
          }

          // test getting swagger-initializer.js, which should contain replaced link to spec
          val initializerJsResp = basicRequest
            .response(asStringAlways)
            .get(uri"http://localhost:$port/${context ++ prefix}/swagger-initializer.js")
            .send(backend)

          if (useRelativePaths) {
            initializerJsResp.body should include(s"./docs.yaml")
          } else {
            initializerJsResp.body should include(s"/${docPathWithTrail}docs.yaml")
          }

          // test getting a swagger-ui resource
          val respCss: Response[String] = basicRequest
            .response(asStringAlways)
            .get(uri"http://localhost:$port/${context ++ prefix}/swagger-ui.css")
            .send(backend)

          respCss.code shouldBe StatusCode.Ok
          respCss.body should include(".swagger-ui")

          // test getting the yaml
          val respYaml = basicRequest
            .response(asStringAlways)
            .get(uri"http://localhost:$port/${context ++ prefix}/docs.yaml")
            .send(backend)

          respYaml.code shouldBe StatusCode.Ok
          respYaml.body should include("paths:")

          // test getting the yaml with `Accepts: application/json, application/yaml`, as that's what SwaggerUI sends (#2396)
          val respYamlAccepts = basicRequest
            .response(asStringAlways)
            .header(Header.accept(MediaType.ApplicationJson, MediaType.unsafeParse("application/yaml")))
            .get(uri"http://localhost:$port/${context ++ prefix}/docs.yaml")
            .send(backend)

          respYamlAccepts.code shouldBe StatusCode.Ok
          respYamlAccepts.body should include("paths:")
        }
      }
  }

  test(s"swagger UI at root, relative path") {
    swaggerUITest(Nil, Nil, useRelativePaths = true).unsafeRunSync()
  }

  test("swagger UI at ./docs endpoint, relative path") {
    swaggerUITest(List("docs"), Nil, useRelativePaths = true).unsafeRunSync()
  }

  test("swagger UI at ./api/docs endpoint, relative path") {
    swaggerUITest(List("api", "docs"), Nil, useRelativePaths = true).unsafeRunSync()
  }

  test(s"swagger UI at root, absolute path") {
    swaggerUITest(Nil, Nil, useRelativePaths = false).unsafeRunSync()
  }

  test("swagger UI at ./docs endpoint, absolute path") {
    swaggerUITest(List("docs"), Nil, useRelativePaths = false).unsafeRunSync()
  }

  test("swagger UI at ./api/docs endpoint, absolute path") {
    swaggerUITest(List("api", "docs"), Nil, useRelativePaths = false).unsafeRunSync()
  }

  test(s"swagger UI at ./api/v1 and empty endpoint, absolute path") {
    swaggerUITest(Nil, List("api", "v1"), useRelativePaths = false).unsafeRunSync()
  }

  test("swagger UI at /internal route /docs endpoint, absolute path") {
    swaggerUITest(List("docs"), List("internal"), useRelativePaths = false).unsafeRunSync()
  }

  test("swagger UI at /internal/secret route /api/docs endpoint, absolute path") {
    swaggerUITest(List("api", "docs"), List("internal", "secret"), useRelativePaths = false).unsafeRunSync()
  }
}
