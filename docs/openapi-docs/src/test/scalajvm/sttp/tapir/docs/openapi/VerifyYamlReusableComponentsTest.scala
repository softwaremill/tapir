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

  test("marked parameters are referenced, identical unmarked ones are still inlined") {
    val tenantId = query[String]("tenantId").description("The tenant").reusableComponent
    val unmarkedTenantId = query[String]("tenantId").description("The tenant")
    val region = query[String]("region").reusableComponent

    val actual = toYaml(
      List(
        endpoint.get.in("books").in(tenantId).out(stringBody),
        endpoint.get.in("magazines").in(tenantId).out(stringBody),
        endpoint.get.in("papers").in(unmarkedTenantId).in(region).out(stringBody)
      )
    )

    actual shouldBe load("reusableComponents/expected_marked_parameters.yml")
  }

  test("marked headers are emitted as parameters or headers, depending on where they are used") {
    val requestId = header[String]("X-Request-Id").description("Correlation id").reusableComponent
    val rateLimit = header[String]("X-Rate-Limit").description("Requests left").reusableComponent("RateLimit")

    val actual = toYaml(List(endpoint.get.in("books").in(requestId).out(stringBody).out(requestId).out(rateLimit)))

    actual shouldBe load("reusableComponents/expected_marked_headers.yml")
  }

  test("a marked header on the error output is emitted as a component") {
    val rateLimit = header[String]("X-Rate-Limit").description("Requests left").reusableComponent

    val actual = toYaml(List(endpoint.get.in("books").out(stringBody).errorOut(stringBody).errorOut(rateLimit)))

    actual shouldBe load("reusableComponents/expected_marked_error_header.yml")
  }

  test("unmarked and hidden parameters produce the document they always produced") {
    val secret = query[String]("secret").schema(_.hidden(true)).reusableComponent

    val actual = toYaml(
      List(
        endpoint.get.in("books").in(secret).in(query[String]("visible")).out(stringBody),
        endpoint.get.in("magazines").in(query[String]("tenantId").description("The tenant")).out(stringBody)
      )
    )

    actual shouldBe load("reusableComponents/expected_unmarked_parameters.yml")
  }
}
