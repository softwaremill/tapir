package sttp.tapir.docs.openapi

import org.scalatest.funsuite.AnyFunSuite
import sttp.tapir._
import sttp.tapir.json.circe.jsonBody

class OpenApiVerifierTest extends AnyFunSuite {
  val openAPISpecification: String =
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

  test("verifyServer - all client openapi endpoints have corresponding server endpoints") {
    val serverEndpoints = List(
      endpoint.get
        .in("users")
        .out(jsonBody[List[String]]),
      endpoint.get
        .in("users" / "name")
        .out(stringBody)
    )

    assert(OpenAPIVerifier.verifyServer(serverEndpoints, openAPISpecification).isEmpty)
  }

  test("verifyServer - additional endpoints in server") {
    val serverEndpoints = List(
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

    assert(OpenAPIVerifier.verifyServer(serverEndpoints, openAPISpecification).isEmpty)
  }

  test("verifyServer - missing endpoints in server") {
    val serverEndpoints = List(
      endpoint.get
        .in("users")
        .out(jsonBody[List[String]])
    )

    assert(OpenAPIVerifier.verifyServer(serverEndpoints, openAPISpecification).nonEmpty)
  }

  test("verifyClient - all server openapi endpoints have corresponding client endpoints") {
    val clientEndpoints = List(
      endpoint.get
        .in("users")
        .out(jsonBody[List[String]]),
      endpoint.get
        .in("users" / "name")
        .out(stringBody)
    )

    assert(OpenAPIVerifier.verifyClient(clientEndpoints, openAPISpecification).isEmpty)
  }

  test("verifyClient - additional endpoints exist in client") {
    val clientEndpoints = List(
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

    assert(OpenAPIVerifier.verifyClient(clientEndpoints, openAPISpecification).nonEmpty)
  }

  test("verifyClient - missing endpoints in client") {
    val clientEndpoints = List(
      endpoint.get
        .in("users")
        .out(jsonBody[List[String]])
    )

    assert(OpenAPIVerifier.verifyClient(clientEndpoints, openAPISpecification).isEmpty)
  }
}
