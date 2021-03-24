package sttp.tapir.docs.openapi

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.tapir.model.UsernamePassword
import sttp.tapir.openapi.Info
import sttp.tapir.openapi.circe.yaml._
import sttp.tapir.{auth, endpoint, header, path, stringBody, _}

import scala.collection.immutable.ListMap

class VerifyYamlSecurityTest extends AnyFunSuite with Matchers {

  test("should support optional authentication") {
    val expectedYaml = load("security/expected_optional_auth.yml")

    val e1 = endpoint.in(auth.bearer[String]()).in("api1" / path[String]).out(stringBody)
    val e2 = endpoint.in(auth.bearer[Option[String]]()).in("api2" / path[String]).out(stringBody)
    val e3 = endpoint.in(auth.apiKey(header[Option[String]]("apikey"))).in("api3" / path[String]).out(stringBody)

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(List(e1, e2, e3), Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should support naming of security schemes") {

    val expectedYaml = load("security/expected_auth_with_named_schemes.yml")

    val e1 = endpoint.in(auth.bearer[String]().securitySchemeName("secBearer")).in("secure" / "bearer").out(stringBody)
    val e2 = endpoint.in(auth.basic[UsernamePassword]().securitySchemeName("secBasic")).in("secure" / "basic").out(stringBody)
    val e3 =
      endpoint.in(auth.apiKey(header[String]("apikey")).securitySchemeName("secApiKeyHeader")).in("secure" / "apiKeyHeader").out(stringBody)

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(List(e1, e2, e3), Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should support Oauth2") {
    val expectedYaml = load("security/expected_oauth2.yml")
    val oauth2 =
      auth.oauth2
        .authorizationCode(
          "https://example.com/auth",
          ListMap("client" -> "scope for clients", "admin" -> "administration scope")
        )

    val e1 =
      endpoint
        .in(oauth2)
        .in("api1" / path[String])
        .out(stringBody)
    val e2 =
      endpoint
        .in(oauth2.requiredScopes(Seq("client")))
        .in("api2" / path[String])
        .out(stringBody)
    val e3 =
      endpoint
        .in(oauth2.requiredScopes(Seq("admin")))
        .in("api3" / path[String])
        .out(stringBody)

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(List(e1, e2, e3), Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

}
