package sttp.tapir.docs.openapi

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.apispec.openapi.Info
import sttp.apispec.openapi.circe.yaml._
import sttp.tapir.tests.OneOfBody.{
  in_one_of_json_text_range_out_string,
  in_one_of_json_xml_hidden_out_string,
  in_one_of_json_xml_text_out_string,
  in_string_out_one_of_json_xml_text
}

class VerifyYamlOneOfBodyTest extends AnyFunSuite with Matchers {
  test("should support input one-of-bodies") {
    val expectedYaml = load("oneOfBody/expected_in_json_xml_text.yml")

    val e = in_one_of_json_xml_text_out_string

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(e, Info("test", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should support input one-of-bodies with ranges") {
    val expectedYaml = load("oneOfBody/expected_in_json_text_range.yml")

    val e = in_one_of_json_text_range_out_string

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(e, Info("test", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should support output one-of-bodies") {
    val expectedYaml = load("oneOfBody/expected_out_json_xml_text.yml")

    val e = in_string_out_one_of_json_xml_text

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(e, Info("test", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should respect hidden body") {
    val expectedYaml = load("oneOfBody/expected_in_json_xml_hidden.yml")

    val e = in_one_of_json_xml_hidden_out_string

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(e, Info("test", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }
}
