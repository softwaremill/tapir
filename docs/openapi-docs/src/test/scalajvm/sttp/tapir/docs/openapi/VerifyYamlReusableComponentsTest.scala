package sttp.tapir.docs.openapi

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.apispec.openapi.Info
import sttp.apispec.openapi.circe.yaml._
import sttp.tapir._
import sttp.tapir.docs.openapi.ReusableComponentAttribute._

class VerifyYamlReusableComponentsTest extends AnyFunSuite with Matchers {

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

  test("a marked response header becomes a components.headers entry") {
    val rateLimit = header[String]("X-Rate-Limit").description("Requests left").reusableComponent

    val actual = toYaml(
      List(
        endpoint.get.in("books").out(stringBody).out(rateLimit),
        endpoint.get.in("magazines").out(stringBody).out(rateLimit)
      )
    )

    actual shouldBe load("reusableComponents/expected_marked_response_header.yml")
  }

  test("the same marked val used as request and as response header appears in both sections") {
    val requestId = header[String]("X-Request-Id").description("Correlation id").reusableComponent

    val openApi = OpenAPIDocsInterpreter(options)
      .toOpenAPI(endpoint.get.in("books").in(requestId).out(stringBody).out(requestId), Info("test", "1.0"))

    openApi.components.map(_.parameters.keys.toList) shouldBe Some(List("X-Request-Id"))
    openApi.components.map(_.headers.keys.toList) shouldBe Some(List("X-Request-Id"))
    operationParametersOf(openApi) shouldBe List(Left("#/components/parameters/X-Request-Id"))
  }

  test("an unmarked response header is still inlined") {
    val openApi = OpenAPIDocsInterpreter(options)
      .toOpenAPI(endpoint.get.in("books").out(stringBody).out(header[String]("X-Rate-Limit")), Info("test", "1.0"))

    openApi.components shouldBe None
  }

  test("a marked response header used by exactly one endpoint is still hoisted") {
    val rateLimit = header[String]("X-Rate-Limit").reusableComponent
    val openApi = OpenAPIDocsInterpreter(options)
      .toOpenAPI(endpoint.get.in("books").out(stringBody).out(rateLimit), Info("test", "1.0"))

    openApi.components.map(_.headers.keys.toList) shouldBe Some(List("X-Rate-Limit"))
  }

  private def operationParametersOf(openApi: sttp.apispec.openapi.OpenAPI): List[Either[String, String]] =
    openApi.paths.pathItems.values.head.get.get.parameters.map {
      case Left(reference)  => Left(reference.$ref)
      case Right(parameter) => Right(parameter.name)
    }
}
