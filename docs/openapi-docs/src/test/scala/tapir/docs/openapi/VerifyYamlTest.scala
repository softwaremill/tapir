package tapir.docs.openapi

import org.scalatest.{FunSuite, Matchers}
import tapir._
import tapir.tests._
import tapir.openapi.circe.yaml._

import scala.io.Source

class VerifyYamlTest extends FunSuite with Matchers {

  val all_the_way: Endpoint[(FruitAmount, String), Unit, FruitAmount] = endpoint
    .in(("fruit" / path[String] / "amount" / path[Int]).mapTo(FruitAmount))
    .in(query[String]("color"))
    .out(stringBody.and(header[Int]("X-Role")).mapTo(FruitAmount))

  test("should match the expected yaml") {
    def noIndentation(s: String) = s.replaceAll("[ \t]", "").trim

    val expectedYaml = noIndentation(Source.fromResource("expected.yml").getLines().mkString("\n"))
    val actualYaml = noIndentation(List(in_query_query_out_string, all_the_way).toOpenAPI("Fruits", "1.0").toYaml)

    actualYaml shouldBe expectedYaml
  }
}
