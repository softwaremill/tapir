package sttp.tapir.docs.openapi

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.model.StatusCode
import sttp.tapir
import sttp.tapir.openapi.Info
import sttp.tapir.openapi.circe.yaml._
import sttp.tapir.tests.Validation
import sttp.tapir.{Endpoint, oneOfMapping, stringBody}

class VerifyYamlDecodeFailureOutputTest extends AnyFunSuite with Matchers {

  private val fallibleEndpoint: Endpoint[Unit, Int, Unit, Unit, Any] = Validation.in_query

  test("should include default 400 response if input decoding may fail") {
    val expectedYaml = load("decode_failure_output/expected_default_400_response.yml")

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(fallibleEndpoint, Info("Entities", "1.0")).toYaml
    noIndentation(actualYaml) shouldBe expectedYaml
  }

  test("should include response defined in options if input decoding may fail") {
    val options: OpenAPIDocsOptions = OpenAPIDocsOptions.default.copy(
      defaultDecodeFailureOutput =
        _ => Some(tapir.oneOf(oneOfMapping(StatusCode.BadRequest, stringBody.description("Description defined in options."))))
    )

    val expectedYaml = load("decode_failure_output/expected_response_defined_in_options.yml")

    val actualYaml = OpenAPIDocsInterpreter(options).toOpenAPI(fallibleEndpoint, Info("Entities", "1.0")).toYaml
    noIndentation(actualYaml) shouldBe expectedYaml
  }

  test("should exclude response if default response is set to None") {
    val options: OpenAPIDocsOptions = OpenAPIDocsOptions.default.copy(defaultDecodeFailureOutput = _ => None)

    val expectedYaml = load("decode_failure_output/expected_no_400_response.yml")

    val actualYaml = OpenAPIDocsInterpreter(options).toOpenAPI(fallibleEndpoint, Info("Entities", "1.0")).toYaml
    noIndentation(actualYaml) shouldBe expectedYaml
  }

  test("should prefer endpoint's error output to default response") {
    val endpointWithDefined400 =
      fallibleEndpoint
        .errorOut(tapir.oneOf(oneOfMapping(StatusCode.BadRequest, stringBody.description("User-defined description."))))

    val expectedYaml = load("decode_failure_output/expected_user_defined_response.yml")

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(endpointWithDefined400, Info("Entities", "1.0")).toYaml
    noIndentation(actualYaml) shouldBe expectedYaml
  }
}
