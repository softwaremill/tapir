package sttp.tapir.docs.openapi

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.apispec.openapi.Info
import sttp.apispec.openapi.circe.yaml._
import sttp.tapir._
import sttp.tapir.docs.openapi.ReusableComponentAttribute._

class VerifyYamlReusableComponentsTest extends AnyFunSuite with Matchers {

  // suppress the default 400 decode-failure response, so the expected YAML is about the feature under test and nothing else
  private val options = OpenAPIDocsOptions.default.copy(defaultDecodeFailureOutput = _ => None)

  private def toYaml(es: List[AnyEndpoint], os: OpenAPIDocsOptions = options): String =
    noIndentation(OpenAPIDocsInterpreter(os).toOpenAPI(es, Info("test", "1.0")).toYaml)

  test("a marked query parameter becomes one component referenced by every operation") {
    val tenantId = query[String]("tenantId").description("The tenant").reusableComponent

    val actual = toYaml(
      List(
        endpoint.get.in("books").in(tenantId).out(stringBody),
        endpoint.get.in("magazines").in(tenantId).out(stringBody)
      )
    )

    actual shouldBe load("reusableComponents/expected_marked_query_two_endpoints.yml")
  }

  test("a marked request header becomes a components.parameters entry with in: header") {
    val requestId = header[String]("X-Request-Id").description("Correlation id").reusableComponent

    val actual = toYaml(List(endpoint.get.in("books").in(requestId).out(stringBody)))

    actual shouldBe load("reusableComponents/expected_marked_request_header.yml")
  }

  test("an unmarked parameter is still inlined, and no components section appears") {
    val openApi = OpenAPIDocsInterpreter(options)
      .toOpenAPI(endpoint.get.in("books").in(query[String]("tenantId").description("The tenant")).out(stringBody), Info("test", "1.0"))

    openApi.components shouldBe None
    operationParametersOf(openApi) shouldBe List(Right("tenantId"))
  }

  test("adding a marked endpoint leaves an unmarked endpoint's operation untouched") {
    val e = endpoint.get.in("books").in(query[String]("tenantId")).out(stringBody)
    val marked = endpoint.get.in("papers").in(query[String]("other").reusableComponent).out(stringBody)

    val alone = OpenAPIDocsInterpreter(options).toOpenAPI(e, Info("test", "1.0"))
    val together = OpenAPIDocsInterpreter(options).toOpenAPI(List(e, marked), Info("test", "1.0"))

    // comparing the PathItem models, not substrings: this also proves the value-keyed lookup does not match a different parameter
    together.paths.pathItems("/books") shouldBe alone.paths.pathItems("/books")
  }

  test("an explicit marker name is used as the component key") {
    val tenantId = query[String]("tenantId").reusableComponent("TenantId")
    val openApi = OpenAPIDocsInterpreter(options).toOpenAPI(endpoint.get.in("books").in(tenantId).out(stringBody), Info("test", "1.0"))

    openApi.components.map(_.parameters.keys.toList) shouldBe Some(List("TenantId"))
    operationParametersOf(openApi) shouldBe List(Left("#/components/parameters/TenantId"))
  }

  test("a marked parameter used by exactly one endpoint is still hoisted") {
    val tenantId = query[String]("tenantId").reusableComponent
    val openApi = OpenAPIDocsInterpreter(options).toOpenAPI(endpoint.get.in("books").in(tenantId).out(stringBody), Info("test", "1.0"))

    openApi.components.map(_.parameters.keys.toList) shouldBe Some(List("tenantId"))
    operationParametersOf(openApi) shouldBe List(Left("#/components/parameters/tenantId"))
  }

  test("a marked but hidden parameter produces no component and no reference") {
    val secret = query[String]("secret").schema(_.hidden(true)).reusableComponent
    val openApi = OpenAPIDocsInterpreter(options)
      .toOpenAPI(endpoint.get.in("books").in(secret).in(query[String]("visible")).out(stringBody), Info("test", "1.0"))

    openApi.components shouldBe None
    operationParametersOf(openApi) shouldBe List(Right("visible"))
  }

  /** Each of the single operation's parameters as either `Left(refString)` or `Right(parameterName)`. */
  private def operationParametersOf(openApi: sttp.apispec.openapi.OpenAPI): List[Either[String, String]] =
    openApi.paths.pathItems.values.head.get.get.parameters.map {
      // an explicit match rather than `.left.map(...)`: LeftProjection's ergonomics differ across 2.12 / 2.13 / 3
      case Left(reference)  => Left(reference.$ref)
      case Right(parameter) => Right(parameter.name)
    }
}
