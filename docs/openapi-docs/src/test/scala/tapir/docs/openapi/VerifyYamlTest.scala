package tapir.docs.openapi

import io.circe.generic.auto._
import org.scalatest.{FunSuite, Matchers}
import tapir._
import tapir.json.circe._
import tapir.model.Method
import tapir.openapi.circe.yaml._
import tapir.tests._

import scala.io.Source

class VerifyYamlTest extends FunSuite with Matchers {

  val all_the_way: Endpoint[(FruitAmount, String), Unit, (FruitAmount, Int), Nothing] = endpoint
    .in(("fruit" / path[String] / "amount" / path[Int]).mapTo(FruitAmount))
    .in(query[String]("color"))
    .out(jsonBody[FruitAmount])
    .out(header[Int]("X-Role"))

  test("should match the expected yaml") {
    val expectedYaml = loadYaml("expected.yml")

    val actualYaml = List(in_query_query_out_string, all_the_way).toOpenAPI("Fruits", "1.0").toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  val endpoint_wit_recursive_structure: Endpoint[Unit, Unit, F1, Nothing] = endpoint
    .out(jsonBody[F1])

  test("should match the expected yaml when schema is recursive") {
    val expectedYaml = loadYaml("expected_recursive.yml")

    val actualYaml = endpoint_wit_recursive_structure.toOpenAPI("Fruits", "1.0").toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should use custom operationId generator") {
    def customOperationIdGenerator(pc: Vector[String], m: Method) = pc.map(_.toUpperCase).mkString("", "+", "-") + m.m.toUpperCase
    val options = OpenAPIDocsOptions.default.copy(customOperationIdGenerator)
    val expectedYaml = loadYaml("expected_custom_operation_id.yml")

    val actualYaml = in_query_query_out_string
      .in("add")
      .in("path")
      .toOpenAPI("Fruits", "1.0")(options)
      .toYaml
    noIndentation(actualYaml) shouldBe expectedYaml
  }

  val streaming_endpoint: Endpoint[Vector[Byte], Unit, Vector[Byte], Vector[Byte]] = endpoint
    .in(streamBody[Vector[Byte]](schemaFor[String], MediaType.TextPlain()))
    .out(streamBody[Vector[Byte]](Schema.SBinary, MediaType.OctetStream()))

  test("should match the expected yaml for streaming endpoints") {
    val expectedYaml = loadYaml("expected_streaming.yml")

    val actualYaml = streaming_endpoint.toOpenAPI("Fruits", "1.0").toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should support tags") {
    val userTaggedEndpointShow = endpoint.tag("user").in("user" / "show").get.out(plainBody[String])
    val userTaggedEdnpointSearch = endpoint.tags(List("user", "admin")).in("user" / "search").get.out(plainBody[String])
    val adminTaggedEndpointAdd = endpoint.tag("admin").in("admin" / "add").get.out(plainBody[String])

    val expectedYaml = loadYaml("expected_tags.yml")

    val actualYaml = List(userTaggedEndpointShow, userTaggedEdnpointSearch, adminTaggedEndpointAdd).toOpenAPI("Fruits", "1.0").toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should support multipart") {
    val expectedYaml = loadYaml("expected_multipart.yml")

    val actualYaml = List(in_file_multipart_out_multipart).toOpenAPI("Fruits", "1.0").toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  private def loadYaml(fileName: String): String = {
    noIndentation(Source.fromResource(fileName).getLines().mkString("\n"))
  }

  private def noIndentation(s: String) = s.replaceAll("[ \t]", "").trim
}

case class F1(data: List[F1])
