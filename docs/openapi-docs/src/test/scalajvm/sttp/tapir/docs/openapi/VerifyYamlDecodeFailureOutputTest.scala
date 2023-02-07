package sttp.tapir.docs.openapi

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.model.StatusCode
import sttp.apispec.openapi.Info
import sttp.apispec.openapi.circe.yaml._
import sttp.tapir
import sttp.tapir._
import sttp.tapir.tests.Validation
import sttp.tapir.{Endpoint, oneOfVariant, stringBody}
import sttp.tapir.json.circe._
import sttp.tapir.generic.auto._

import io.circe.generic.auto._

class VerifyYamlDecodeFailureOutputTest extends AnyFunSuite with Matchers {

  private val fallibleEndpoint: Endpoint[Unit, Int, Unit, Unit, Any] = Validation.in_query
  private case class Fail(msg: String)

  test("should include default 400 response if input decoding may fail") {
    val expectedYaml = load("decode_failure_output/expected_default_400_response.yml")

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(fallibleEndpoint, Info("Entities", "1.0")).toYaml
    noIndentation(actualYaml) shouldBe expectedYaml
  }

  test("should include response defined in options if input decoding may fail") {
    val options: OpenAPIDocsOptions = OpenAPIDocsOptions.default.copy(
      defaultDecodeFailureOutput =
        _ => Some(tapir.oneOf(oneOfVariant(StatusCode.BadRequest, stringBody.description("Description defined in options."))))
    )

    val expectedYaml = load("decode_failure_output/expected_response_defined_in_options.yml")

    val actualYaml = OpenAPIDocsInterpreter(options).toOpenAPI(fallibleEndpoint, Info("Entities", "1.0")).toYaml
    noIndentation(actualYaml) shouldBe expectedYaml
  }

  test("should include json response defined in options if input decoding may fail") {
    val options: OpenAPIDocsOptions = OpenAPIDocsOptions.default.copy(
      defaultDecodeFailureOutput =
        _ => Some(statusCode(StatusCode.BadRequest).and(jsonBody[Fail]))
    )

    val expectedYaml = load("decode_failure_output/expected_json_response_defined_in_options.yml")

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
        .errorOut(tapir.oneOf(oneOfVariant(StatusCode.BadRequest, stringBody.description("User-defined description."))))

    val expectedYaml = load("decode_failure_output/expected_user_defined_response.yml")

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(endpointWithDefined400, Info("Entities", "1.0")).toYaml
    noIndentation(actualYaml) shouldBe expectedYaml
  }
}
