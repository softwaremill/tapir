package sttp.tapir.docs.openapi

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.apispec.openapi.Info
import sttp.apispec.openapi.circe.yaml._
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.model.UsernamePassword
import sttp.tapir.tests.data.FruitAmount
import sttp.tapir.{auth, endpoint, header, path, stringBody, _}
import sttp.tapir.generic.Derived
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import io.circe.generic.auto._

import scala.collection.immutable.{ListMap, Seq}

class VerifyYamlSecurityTest extends AnyFunSuite with Matchers {

  test("should support authentication") {
    val expectedYaml = load("security/expected_auth.yml")

    val e1 = endpoint.securityIn(auth.bearer[String]()).in("api1" / path[String]).out(stringBody)
    val e2 = endpoint.securityIn(auth.bearer[String]()).in("api2" / path[String]).out(stringBody)
    val e3 = endpoint.securityIn(auth.apiKey(header[String]("apikey"))).in("api3" / path[String]).out(stringBody)

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(List(e1, e2, e3), Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should support optional authentication") {
    val expectedYaml = load("security/expected_optional_auth.yml")

    val e1 = endpoint.securityIn(auth.bearer[String]()).in("api1" / path[String]).out(stringBody)
    val e2 = endpoint.securityIn(auth.bearer[Option[String]]()).in("api2" / path[String]).out(stringBody)
    val e3 = endpoint.securityIn(auth.apiKey(header[Option[String]]("apikey"))).in("api3" / path[String]).out(stringBody)

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(List(e1, e2, e3), Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should support multiple optional authentications as alternative authentication methods") {
    val expectedYaml = load("security/expected_multiple_optional_auth.yml")

    val e1 = endpoint
      .securityIn("api1")
      .securityIn(auth.apiKey(header[Option[String]]("apikey1")).securitySchemeName("sec1"))
      .securityIn(auth.apiKey(header[Option[String]]("apikey2")).securitySchemeName("sec2"))

    val e2 = endpoint
      .securityIn("api2")
      .securityIn(auth.apiKey(header[Option[String]]("apikey1")).securitySchemeName("sec1"))
      .securityIn(auth.apiKey(header[Option[String]]("apikey2")).securitySchemeName("sec2"))
      .securityIn(emptyAuth)

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(List(e1, e2), Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should support optional oauth2 and other optional authentications as alternative authentication methods") {
    val expectedYaml = load("security/expected_multiple_optional_auth_oauth2.yml")

    val e1 = endpoint
      .securityIn("api1")
      .securityIn(
        auth.oauth2
          .clientCredentialsFlowOptional(
            "https://example.com/token",
            Some("https://example.com/token/refresh"),
            ListMap("client" -> "scope for clients", "admin" -> "administration scope")
          )
          .securitySchemeName("sec1")
      )
      .securityIn(auth.apiKey(header[Option[String]]("apikey2")).securitySchemeName("sec2"))

    val e2 = endpoint
      .securityIn("api2")
      .securityIn(
        auth.oauth2
          .clientCredentialsFlowOptional(
            "https://example.com/token",
            Some("https://example.com/token/refresh"),
            ListMap("client" -> "scope for clients", "admin" -> "administration scope")
          )
          .securitySchemeName("sec1")
      )
      .securityIn(auth.apiKey(header[Option[String]]("apikey2")).securitySchemeName("sec2"))
      .securityIn(emptyAuth)

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(List(e1, e2), Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should support groups of optional authentications") {
    val expectedYaml = load("security/expected_multiple_optional_grouped_auth.yml")

    val e1 = endpoint
      .securityIn("api1")
      .securityIn(auth.apiKey(header[Option[String]]("apikey1")).securitySchemeName("sec1").group("g1"))
      .securityIn(auth.apiKey(header[Option[String]]("apikey2")).securitySchemeName("sec2").group("g1"))
      .securityIn(auth.apiKey(header[Option[String]]("apikey3")).securitySchemeName("sec3"))

    val e2 = endpoint
      .securityIn("api2")
      .securityIn(auth.apiKey(header[Option[String]]("apikey1")).securitySchemeName("sec1").group("g1"))
      .securityIn(auth.apiKey(header[Option[String]]("apikey2")).securitySchemeName("sec2").group("g1"))
      .securityIn(auth.apiKey(header[Option[String]]("apikey3")).securitySchemeName("sec3"))
      .securityIn(emptyAuth)

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(List(e1, e2), Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should support naming of security schemes") {

    val expectedYaml = load("security/expected_auth_with_named_schemes.yml")

    val e1 = endpoint.securityIn(auth.bearer[String]().securitySchemeName("secBearer")).in("secure" / "bearer").out(stringBody)
    val e2 = endpoint.securityIn(auth.basic[UsernamePassword]().securitySchemeName("secBasic")).in("secure" / "basic").out(stringBody)
    val e3 =
      endpoint
        .securityIn(auth.apiKey(header[String]("apikey")).securitySchemeName("secApiKeyHeader"))
        .in("secure" / "apiKeyHeader")
        .out(stringBody)

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(List(e1, e2, e3), Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should support descriptions in security schemes") {
    val expectedYaml = load("security/expected_auth_with_description.yml")

    val e = endpoint.securityIn(auth.bearer[String]().description("put your token here")).in("secure" / "bearer").out(stringBody)

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(List(e), Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should support bearerFormat in security schemes") {
    val expectedYaml = load("security/expected_auth_with_bearer_format.yml")

    val e = endpoint.securityIn(auth.bearer[String]().bearerFormat("JWT")).in("secure" / "bearer").out(stringBody)

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(List(e), Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should support Oauth2") {
    val expectedYaml = load("security/expected_oauth2.yml")
    val authCodeFlow =
      auth.oauth2
        .authorizationCodeFlow(
          "https://example.com/auth",
          "https://example.com/token",
          Some("https://example.com/token/refresh"),
          ListMap("client" -> "scope for clients", "admin" -> "administration scope")
        )
    val clientCredFlow =
      auth.oauth2
        .clientCredentialsFlow(
          "https://example.com/token",
          Some("https://example.com/token/refresh"),
          ListMap("client" -> "scope for clients", "admin" -> "administration scope")
        )
    val implicitFlow =
      auth.oauth2
        .implicitFlow(
          "https://example.com/auth",
          Some("https://example.com/token/refresh"),
          ListMap("client" -> "scope for clients", "admin" -> "administration scope")
        )

    val e1 =
      endpoint
        .securityIn(authCodeFlow)
        .in("api1" / path[String])
        .out(stringBody)
    val e2 =
      endpoint
        .securityIn(clientCredFlow.requiredScopes(Seq("client")))
        .in("api2" / path[String])
        .out(stringBody)
    val e3 =
      endpoint
        .securityIn(implicitFlow.requiredScopes(Seq("admin")))
        .in("api3" / path[String])
        .out(stringBody)

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(List(e1, e2, e3), Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should respect hidden annotation for security body") {
    val e = endpoint.post
      .securityIn(byteArrayBody.schema(_.hidden(true)))
      .in("api" / "echo")
      .in(jsonBody[FruitAmount])
      .out(header[List[String]]("Set-Cookie"))

    val actualYaml = OpenAPIDocsInterpreter()
      .toOpenAPI(e, Info("Hide security body", "1.0"))
      .toYaml

    val expectedYaml = load("security/expected_hide_security_body.yml")

    noIndentation(actualYaml) shouldBe expectedYaml
  }
}
