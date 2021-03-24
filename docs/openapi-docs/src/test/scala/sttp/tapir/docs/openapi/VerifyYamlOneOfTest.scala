package sttp.tapir.docs.openapi

import io.circe.generic.auto._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.model.StatusCode
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.openapi.Info
import sttp.tapir.openapi.circe.yaml._
import sttp.tapir.{Codec, CodecFormat, Schema, SchemaType, endpoint, header, plainBody, statusCode, statusDefaultMapping, statusMapping}

class VerifyYamlOneOfTest extends AnyFunSuite with Matchers {

  test("should support multiple status codes") {
    val expectedYaml = load("oneOf/expected_status_codes.yml")

    // work-around for #10: unsupported sealed trait families
    implicit val schemaForErrorInfo: Schema[ErrorInfo] = Schema[ErrorInfo](SchemaType.SProduct(SchemaType.SObjectInfo("ErrorInfo"), Nil))

    val e = endpoint.errorOut(
      sttp.tapir.oneOf(
        statusMapping(StatusCode.NotFound, jsonBody[NotFound].description("not found")),
        statusMapping(StatusCode.Unauthorized, jsonBody[Unauthorized].description("unauthorized")),
        statusDefaultMapping(jsonBody[Unknown].description("unknown"))
      )
    )

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(e, Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should support multiple the same status codes") {
    val expectedYaml = load("oneOf/expected_the_same_status_codes.yml")

    implicit val unauthorizedTextPlainCodec: Codec[String, Unauthorized, CodecFormat.TextPlain] =
      Codec.string.map(Unauthorized.apply _)(_.realm)

    val e = endpoint.out(
      sttp.tapir.oneOf(
        statusMapping(StatusCode.Ok, jsonBody[NotFound].description("not found")),
        statusMapping(StatusCode.Ok, plainBody[Unauthorized]),
        statusMapping(StatusCode.NoContent, jsonBody[Unknown].description("unknown"))
      )
    )

    val actualYaml = OpenAPIDocsInterpreter.toOpenAPI(e, Info("Fruits", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("use status codes declared with description") {
    val expectedYaml = load("oneOf/expected_one_of_status_codes.yml")

    val actualYaml = OpenAPIDocsInterpreter
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

}
