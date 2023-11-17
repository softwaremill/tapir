package sttp.tapir.docs.openapi

import io.circe.generic.auto._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.apispec.openapi.Info
import sttp.apispec.openapi.circe.yaml._
import sttp.tapir.Schema.annotations.{deprecated, description}
import sttp.tapir.generic.auto._
import sttp.tapir._
import sttp.tapir.json.circe._

/** Tests which check that multiple, independent customisations of schemas for same data types don't get lost / overridden. Such
  * customisations should be moved to the `$ref` (as allowed by OpenAPI 3.1), or kept inline, if the schema doesn't have a name.
  */
class VerifyYamlMultiCustomiseSchemaTest extends AnyFunSuite with Matchers {
  import VerifyYamlMultiCustomiseSchemaTest._

  test("nested body references") {
    val e = endpoint.get.in(jsonBody[Data2])
    val expectedYaml = load("multi_customise_schema/nested_body.yml")

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(List(e), Info("Schemas", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("inlined schemas") {
    val e = endpoint.get.in(jsonBody[Data2].schema(_.modify(_.a)(_.name(None)).modify(_.b)(_.name(None))))
    val expectedYaml = load("multi_customise_schema/inlined.yml")

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(List(e), Info("Schemas", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("top-level body references") {
    val e = endpoint.get.in(jsonBody[Data1].schema(_.description("d1"))).out(jsonBody[Data1].schema(_.description("d2")))
    val expectedYaml = load("multi_customise_schema/top_level_body.yml")

    val actualYaml = OpenAPIDocsInterpreter().toOpenAPI(List(e), Info("Schemas", "1.0")).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("deprecated optional reference field, when there's also a non-deprecated one") {
    val expectedYaml = load("multi_customise_schema/expected_deprecated_optional_field.yml")
    val actualYaml = OpenAPIDocsInterpreter()
      .toOpenAPI(endpoint.in(jsonBody[HasOptionalDeprecated]), Info("Entities", "1.0"))
      .toYaml

    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }

  test("deprecated optional array field, when there's also a non-deprecated one") {
    val expectedYaml = load("multi_customise_schema/expected_deprecated_array_field.yml")
    val actualYaml = OpenAPIDocsInterpreter()
      .toOpenAPI(endpoint.in(jsonBody[HasCollectionDeprecated]), Info("Entities", "1.0"))
      .toYaml

    val actualYamlNoIndent = noIndentation(actualYaml)
    actualYamlNoIndent shouldBe expectedYaml
  }
}

object VerifyYamlMultiCustomiseSchemaTest {
  case class Data1(x: String)
  case class Data2(@deprecated @description("aaa") a: Data1, @description("bbb") b: Data1)

  case class HasOptionalDeprecated(field1: Data1, @Schema.annotations.deprecated field2: Option[Data1])
  case class HasCollectionDeprecated(field1: List[Data1], @Schema.annotations.deprecated field2: List[Data1])
}
