package tapir.docs.openapi

import org.scalatest.{FunSuite, Matchers}
import tapir._
import tapir.json.circe._
import tapir.tests._
import tapir.openapi.circe.yaml._
import io.circe.generic.auto._

import scala.io.Source

class VerifyYamlTest extends FunSuite with Matchers {

  val all_the_way: Endpoint[(FruitAmount, String), Unit, (FruitAmount, Int)] = endpoint
    .in(("fruit" / path[String] / "amount" / path[Int]).mapTo(FruitAmount))
    .in(query[String]("color"))
    .out(jsonBody[FruitAmount])
    .out(header[Int]("X-Role"))

  test("should match the expected yaml") {
    val expectedYaml = noIndentation(Source.fromResource("expected.yml").getLines().mkString("\n"))

    val actualYaml = List(in_query_query_out_string, all_the_way).toOpenAPI("Fruits", "1.0").toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  val endpoint_wit_recursive_structure: Endpoint[Unit, Unit, F1] = endpoint
    .out(jsonBody[F1])

  test("should match the expected yaml when schema is recursive") {
    val expectedYaml = noIndentation(Source.fromResource("expected_recursive.yml").getLines().mkString("\n"))

    val actualYaml = endpoint_wit_recursive_structure.toOpenAPI("Fruits", "1.0").toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  case class F1(data: List[F1])

  private def noIndentation(s: String) = s.replaceAll("[ \t]", "").trim
}
