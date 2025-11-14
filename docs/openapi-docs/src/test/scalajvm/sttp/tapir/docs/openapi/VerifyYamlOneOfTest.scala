package sttp.tapir.docs.openapi

import io.circe.generic.auto._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.model.StatusCode
import sttp.apispec.openapi.Info
import sttp.apispec.openapi.circe.yaml._
import sttp.tapir.docs.openapi.VerifyYamlOneOfTest._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.model.StatusCodeRange
import sttp.tapir.tests.ContentNegotiation
import sttp.tapir.{Codec, CodecFormat, emptyOutput, endpoint, header, oneOfDefaultVariant, oneOfVariant, plainBody, statusCode}

class VerifyYamlOneOfTest extends AnyFunSuite with Matchers {

  test("should support description for status code mappings with empty output") {
    val expectedYaml = load("oneOf/expected_status_codes_with_empty_output.yaml")

    val e = endpoint.errorOut(sttp.tapir.oneOf(oneOfVariant(StatusCode.Forbidden, emptyOutput.description("forbidden"))))

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(e, Info("test", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should support multiple status codes") {
    val expectedYaml = load("oneOf/expected_status_codes.yml")

    val e = endpoint.errorOut(
      sttp.tapir.oneOf(
        oneOfVariant(StatusCode.Unauthorized, jsonBody[Unauthorized].description("unauthorized")),
        oneOfVariant(StatusCode.NotFound, jsonBody[NotFound].description("not found").example(NotFound("not found"))),
        oneOfVariant(StatusCode.NotFound, jsonBody[NotFound].description("404").example(NotFound("404"))),
        oneOfDefaultVariant(jsonBody[Unknown].description("unknown"))
      )
    )

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(e, Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should support multiple status codes when defined in multiple steps") {
    val expectedYaml = load("oneOf/expected_status_codes.yml")

    val e = endpoint
      .errorOut(jsonBody[Unknown].description("unknown"))
      .errorOutVariants[ErrorInfo](
        oneOfVariant(StatusCode.Unauthorized, jsonBody[Unauthorized].description("unauthorized")),
        oneOfVariant(StatusCode.NotFound, jsonBody[NotFound].description("not found").example(NotFound("not found"))),
        oneOfVariant(StatusCode.NotFound, jsonBody[NotFound].description("404").example(NotFound("404")))
      )

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(e, Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should support multiple the same status codes and same content types") {
    val expectedYaml = load("oneOf/expected_the_same_status_codes_and_content_types.yml")

    val e = endpoint.out(
      sttp.tapir.oneOf(
        oneOfVariant(StatusCode.Ok, jsonBody[NotFound].description("not found")),
        oneOfVariant(StatusCode.Ok, jsonBody[Unauthorized]),
        oneOfVariant(StatusCode.NoContent, jsonBody[Unknown].description("unknown"))
      )
    )

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(e, Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should support multiple the same status codes and different content types") {
    val expectedYaml = load("oneOf/expected_the_same_status_codes.yml")

    implicit val unauthorizedTextPlainCodec: Codec[String, Unauthorized, CodecFormat.TextPlain] =
      Codec.string.map(Unauthorized.apply _)(_.realm)

    val e = endpoint.out(
      sttp.tapir.oneOf(
        oneOfVariant(StatusCode.Ok, jsonBody[NotFound].description("not found")),
        oneOfVariant(StatusCode.Ok, plainBody[Unauthorized]),
        oneOfVariant(StatusCode.NoContent, jsonBody[Unknown].description("unknown"))
      )
    )

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(e, Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("use status codes declared with description") {
    val expectedYaml = load("oneOf/expected_one_of_status_codes.yml")

    val actualYaml = OpenAPIDocsInterpreter()
      .toOpenAPI(
        endpoint
          .out(header[String]("Location"))
          .errorOut(statusCode.description(StatusCode.NotFound, "entity not found").description(StatusCode.BadRequest, "")),
        Info("Entities", "1.0")
      )
      .toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("use declared status code range with description") {
    val expectedYaml = load("oneOf/expected_one_of_status_code_range.yml")

    val actualYaml = OpenAPIDocsInterpreter()
      .toOpenAPI(
        endpoint
          .out(header[String]("Location"))
          .errorOut(statusCode.description(StatusCode.NotFound, "not here").description(StatusCodeRange.ClientError, "bad input")),
        Info("Entities", "1.0")
      )
      .toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should match the expected yaml with multiple media types for common schema") {
    val expectedYaml = load("oneOf/expected_multiple_media_types_common_schema.yml")

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(ContentNegotiation.out_json_xml_text_common_schema, Info("Examples", "1.0")).toYaml

    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should match the expected yaml with multiple media types for different schema") {
    val expectedYaml = load("oneOf/expected_multiple_media_types_different_schema.yml")

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(ContentNegotiation.out_json_xml_different_schema, Info("Examples", "1.0")).toYaml

    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

}

object VerifyYamlOneOfTest {
  sealed trait ErrorInfo
  case class NotFound(what: String) extends ErrorInfo
  case class Unauthorized(realm: String) extends ErrorInfo
  case class Unknown(code: Int, msg: String) extends ErrorInfo
}
