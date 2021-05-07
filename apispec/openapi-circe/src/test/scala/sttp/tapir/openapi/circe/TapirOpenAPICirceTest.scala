package sttp.tapir.openapi.circe

import sttp.tapir.apispec.ReferenceOr
import sttp.tapir.openapi._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.collection.immutable.ListMap
import scala.io.Source

class TapirOpenAPICirceTest extends AnyFunSuite with Matchers {

  test("should match the expected yaml with schema dialect") {
    val expectedYaml = load("expected_with_schema_dialect.yml")
    val responsesList = ListMap[ResponsesKey, ReferenceOr[Response]](ResponsesCodeKey(200) -> Right(Response(description = "Default description")))
    val responses = Responses(responsesList)
    val operation = Operation(operationId = "getRoot", responses = responses)
    val pathItem = PathItem(get = Some(operation))

    val actualYaml = OpenAPI(
      info = Info("Fruits", "1.0"),
      jsonSchemaDialect = Some("https://json-schema.org/draft/2020-12/schema"),
      tags = List.empty,
      servers = List.empty,
      paths = Paths(ListMap[String, PathItem]("/" -> pathItem)),
      webhooks = None,
      components = None,
      security = List.empty)
      .toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should match the expected yaml with webhooks") {
    val expectedYaml = load("expected_webhooks.yml")
    val responsesList = ListMap[ResponsesKey, ReferenceOr[Response]](ResponsesCodeKey(200) -> Right(Response(description = "Default description")))
    val responses = Responses(responsesList)
    val operation = Operation(operationId = "getRoot", responses = responses)
    val eitherPathItem = Right(PathItem(get = Some(operation)))
    val pathItem = PathItem(get = Some(operation))

    val actualYaml = OpenAPI(
      info = Info("Fruits", "1.0"),
      jsonSchemaDialect = None,
      tags = List.empty,
      servers = List.empty,
      paths = Paths(ListMap[String, PathItem]("/" -> pathItem)),
      webhooks = Some(Map("newPet" -> eitherPathItem)),
      components = None,
      security = List.empty)
      .toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  private def load(fileName: String): String = {
    noIndentation(Source.fromInputStream(getClass.getResourceAsStream(s"/$fileName")).getLines().mkString("\n"))
  }
  private def noIndentation(s: String): String = s.replaceAll("[ \t]", "").trim

}
