package sttp.tapir.docs.openapi

import org.scalatest.funsuite.AnyFunSuite
import sttp.tapir._
import sttp.tapir.json.circe.jsonBody

class OpenApiVerifierTest extends AnyFunSuite {
  val yamlString: String =
    """openapi: 3.0.0
      |info:
      |  title: Sample API
      |  description: Optional multiline or single-line description in [CommonMark](http://commonmark.org/help/) or HTML.
      |  version: 0.1.9
      |
      |servers:
      |  - url: http://api.example.com/v1
      |    description: Optional server description, e.g. Main (production) server
      |  - url: http://staging-api.example.com
      |    description: Optional server description, e.g. Internal staging server for testing
      |
      |paths:
      |  /users:
      |    get:
      |      summary: Returns a list of users.
      |      description: Optional extended description in CommonMark or HTML.
      |      responses:
      |        "200": # status code
      |          description: A JSON array of user names
      |          content:
      |            application/json:
      |              schema:
      |                type: array
      |                items:
      |                  type: string
      |  /users/name:
      |    get:
      |      summary: Returns a user name.
      |      description: Retrieves the name of a specific user.
      |      responses:
      |        "200": # status code
      |          description: A plain text user name
      |          content:
      |            text/plain:
      |              schema:
      |                type: string
      """.stripMargin

  test("exact mode - all openapi endpoints have corresponding tapir endpoints") {
    val tapirEndpoints = List(
      endpoint.get
        .in("users")
        .out(jsonBody[List[String]]),
      endpoint.get
        .in("users" / "name")
        .out(stringBody)
    )

    val openAPIVerifier = OpenAPIVerifier(tapirEndpoints, yamlString, Mode.Exact)
    assert(openAPIVerifier.verify().isEmpty)
  }

  test("exact mode - additional endpoints in tapir") {
    val tapirEndpoints = List(
      endpoint.get
        .in("users")
        .out(jsonBody[List[String]]),
      endpoint.get
        .in("users" / "name")
        .out(stringBody),
      endpoint.get
        .in("extra")
        .out(stringBody)
    )

    val openAPIVerifier = OpenAPIVerifier(tapirEndpoints, yamlString, Mode.Exact)
    assert(openAPIVerifier.verify().nonEmpty)
  }

  test("exact mode - missing endpoints exist in tapir") {
    val tapirEndpoints = List(
      endpoint.get
        .in("users")
        .out(jsonBody[List[String]])
    )

    val openAPIVerifier = OpenAPIVerifier(tapirEndpoints, yamlString, Mode.Exact)
    assert(openAPIVerifier.verify().nonEmpty)
  }

  test("at-least mode - all openapi endpoints have corresponding tapir endpoints") {
    val tapirEndpoints = List(
      endpoint.get
        .in("users")
        .out(jsonBody[List[String]]),
      endpoint.get
        .in("users" / "name")
        .out(stringBody)
    )

    val openAPIVerifier = OpenAPIVerifier(tapirEndpoints, yamlString, Mode.AtLeast)
    assert(openAPIVerifier.verify().isEmpty)
  }

  test("at-least mode - additional endpoints exist in tapir") {
    val tapirEndpoints = List(
      endpoint.get
        .in("users")
        .out(jsonBody[List[String]]),
      endpoint.get
        .in("users" / "name")
        .out(stringBody),
      endpoint.get
        .in("extra")
        .out(stringBody)
    )

    val openAPIVerifier = OpenAPIVerifier(tapirEndpoints, yamlString, Mode.AtLeast)
    assert(openAPIVerifier.verify().isEmpty)
  }

  test("at-least mode - missing endpoints exist in tapir") {
    val tapirEndpoints = List(
      endpoint.get
        .in("users")
        .out(jsonBody[List[String]])
    )

    val openAPIVerifier = OpenAPIVerifier(tapirEndpoints, yamlString, Mode.AtLeast)
    assert(openAPIVerifier.verify().nonEmpty)
  }

  test("at-most mode - all openapi endpoints have corresponding tapir endpoints") {
    val tapirEndpoints = List(
      endpoint.get
        .in("users")
        .out(jsonBody[List[String]]),
      endpoint.get
        .in("users" / "name")
        .out(stringBody)
    )

    val openAPIVerifier = OpenAPIVerifier(tapirEndpoints, yamlString, Mode.AtMost)
    assert(openAPIVerifier.verify().isEmpty)
  }

  test("at-most mode - additional endpoint exist in tapir") {
    val tapirEndpoints = List(
      endpoint.get
        .in("users")
        .out(jsonBody[List[String]]),
      endpoint.get
        .in("users" / "name")
        .out(stringBody),
      endpoint.get
        .in("extra")
        .out(stringBody)
    )

    val openAPIVerifier = OpenAPIVerifier(tapirEndpoints, yamlString, Mode.AtMost)
    assert(openAPIVerifier.verify().nonEmpty)
  }

  test("at-most mode - missing endpoint in tapir") {
    val tapirEndpoints = List(
      endpoint.get
        .in("users")
        .out(jsonBody[List[String]])
    )

    val openAPIVerifier = OpenAPIVerifier(tapirEndpoints, yamlString, Mode.AtMost)
    assert(openAPIVerifier.verify().isEmpty)
  }
}
