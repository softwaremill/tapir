package sttp.tapir.openapi.circe

import io.circe.Printer
import io.circe.syntax.EncoderOps
import sttp.tapir.apispec.{Reference, ReferenceOr, Schema, SchemaType}
import sttp.tapir.openapi._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.collection.immutable.ListMap
import scala.io.Source

class TapirOpenAPICirceTest extends AnyFunSuite with Matchers {

  test("should match the expected json with schema dialect") {
    val expectedJson = load("expected_with_schema_dialect.json")
    val responsesList = ListMap[ResponsesKey, ReferenceOr[Response]](ResponsesCodeKey(200) -> Right(Response(description = "Default description")))
    val responses = Responses(responsesList)
    val operation = Operation(operationId = "getRoot", responses = responses)
    val pathItem = PathItem(get = Some(operation))

    val actualJson = OpenAPI(
      info = Info("Fruits", "1.0"),
      jsonSchemaDialect = Some("https://json-schema.org/draft/2020-12/schema"),
      tags = List.empty,
      servers = List.empty,
      paths = Paths(ListMap[String, PathItem]("/" -> pathItem)),
      webhooks = None,
      components = None,
      security = List.empty)
      .asJson
    val actualJsonNoIndent = noIndentation(Printer.spaces2.print(actualJson))

    actualJsonNoIndent shouldBe expectedJson
  }

  test("should match the expected json with webhooks") {
    val expectedJson = load("expected_webhooks.json")
    val responsesList = ListMap[ResponsesKey, ReferenceOr[Response]](ResponsesCodeKey(200) -> Right(Response(description = "Default description")))
    val responses = Responses(responsesList)
    val operation = Operation(operationId = "getRoot", responses = responses)
    val eitherPathItem = Right(PathItem(get = Some(operation)))
    val pathItem = PathItem(get = Some(operation))

    val actualJson= OpenAPI(
      info = Info("Fruits", "1.0"),
      jsonSchemaDialect = None,
      tags = List.empty,
      servers = List.empty,
      paths = Paths(ListMap[String, PathItem]("/" -> pathItem)),
      webhooks = Some(Map("newPet" -> eitherPathItem)),
      components = None,
      security = List.empty)
      .asJson
    val actualJsonNoIndent = noIndentation(Printer.spaces2.print(actualJson))

    actualJsonNoIndent shouldBe expectedJson
  }

  test("should match the expected json with ref along with description") {
    val expectedJson = load("expected_with_ref.json")
    val content = ListMap[String, MediaType]("application/json" -> MediaType(schema = Some(Left(Reference(
      "#/components/schemas/Person", Some("Example summary"), Some("Person with name and age"))
    ))))
    val response = new Response(description = "Get Person ok response", content = content)
    val responsesList = ListMap[ResponsesKey, ReferenceOr[Response]](ResponsesCodeKey(200) -> Right(response))
    val operation = Operation(operationId = "getPerson", responses = Responses(responsesList))
    val properties = ListMap[String, ReferenceOr[Schema]](
      "name" -> Right(new Schema(`type` = Some(SchemaType.String))),
      "age" -> Right(new Schema(`type` = Some(SchemaType.Integer)))
    )
    val schema = new Schema(`type` = Some(SchemaType.Object), properties = properties)
    val schemas = ListMap[String, ReferenceOr[Schema]]("Person" -> Right(schema))
    val components = Components(schemas = schemas, securitySchemes = ListMap.empty)

    val actualJson= OpenAPI(
      info = Info("Persons", "1.0"),
      jsonSchemaDialect = None,
      tags = List.empty,
      servers = List.empty,
      paths = Paths(ListMap[String, PathItem]("/" -> PathItem(get = Some(operation)))),
      webhooks = None,
      components = Some(components),
      security = List.empty)
      .asJson
    val actualJsonNoIndent = noIndentation(Printer.spaces2.print(actualJson))

    actualJsonNoIndent shouldBe expectedJson
  }

  private def load(fileName: String): String = {
    noIndentation(Source.fromInputStream(getClass.getResourceAsStream(s"/$fileName")).getLines().mkString("\n"))
  }
  private def noIndentation(s: String): String = s.replaceAll("[ \t]", "").trim

}

case class Person(name: String, age: Integer)