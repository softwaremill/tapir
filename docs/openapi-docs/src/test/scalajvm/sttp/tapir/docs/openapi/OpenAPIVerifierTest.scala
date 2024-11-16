package sttp.tapir.docs.openapi

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.apispec.openapi.Info
import sttp.tapir._
import sttp.tapir.docs.openapi.OpenAPIVerifier.Mode.{AtLeast, AtMost, Exact}

class OpenAPIVerifierTest extends AnyFunSuite with Matchers {

  val sampleEndpoints: List[AnyEndpoint] = List(
    endpoint.get.in("path1").out(stringBody),
    endpoint.post.in("path2").in(jsonBody[Int]).out(jsonBody[String])
  )

  val sampleOpenApiSpec: String =
    """
      |openapi: 3.0.0
      |info:
      |  title: Sample API
      |  version: 1.0.0
      |paths:
      |  /path1:
      |    get:
      |      responses:
      |        '200':
      |          description: OK
      |          content:
      |            text/plain:
      |              schema:
      |                type: string
      |  /path2:
      |    post:
      |      requestBody:
      |        content:
      |          application/json:
      |            schema:
      |              type: integer
      |      responses:
      |        '200':
      |          description: OK
      |          content:
      |            application/json:
      |              schema:
      |                type: string
      |""".stripMargin

  test("OpenAPIVerifier should verify exact match") {
    val result = OpenAPIVerifier.verify(sampleEndpoints, sampleOpenApiSpec, Exact)
    result shouldBe Right(())
  }

  test("OpenAPIVerifier should verify at-least match") {
    val result = OpenAPIVerifier.verify(sampleEndpoints, sampleOpenApiSpec, AtLeast)
    result shouldBe Right(())
  }

  test("OpenAPIVerifier should verify at-most match") {
    val result = OpenAPIVerifier.verify(sampleEndpoints, sampleOpenApiSpec, AtMost)
    result shouldBe Right(())
  }

  val additionalOpenApiSpec: String =
    """
      |openapi: 3.0.0
      |info:
      |  title: Sample API
      |  version: 1.0.0
      |paths:
      |  /path1:
      |    get:
      |      responses:
      |        '200':
      |          description: OK
      |          content:
      |            text/plain:
      |              schema:
      |                type: string
      |  /path2:
      |    post:
      |      requestBody:
      |        content:
      |          application/json:
      |            schema:
      |              type: integer
      |      responses:
      |        '200':
      |          description: OK
      |          content:
      |            application/json:
      |              schema:
      |                type: string
      |  /path3:
      |    put:
      |      requestBody:
      |        content:
      |          application/json:
      |            schema:
      |              type: string
      |      responses:
      |        '200':
      |          description: OK
      |          content:
      |            application/json:
      |              schema:
      |                type: string
      |""".stripMargin

  test("OpenAPIVerifier should fail exact match with additional endpoints") {
    val result = OpenAPIVerifier.verify(sampleEndpoints, additionalOpenApiSpec, Exact)
    result shouldBe a[Left[_, _]]
  }

  test("OpenAPIVerifier should pass at-least match with additional endpoints") {
    val result = OpenAPIVerifier.verify(sampleEndpoints, additionalOpenApiSpec, AtLeast)
    result shouldBe Right(())
  }

  test("OpenAPIVerifier should fail at-most match with additional endpoints") {
    val result = OpenAPIVerifier.verify(sampleEndpoints, additionalOpenApiSpec, AtMost)
    result shouldBe a[Left[_, _]]
  }
}
