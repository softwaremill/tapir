package sttp.tapir.docs.asyncapi

import io.circe.generic.auto._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.capabilities.akka.AkkaStreams
import sttp.tapir.asyncapi.circe.yaml.RichAsyncAPI
import sttp.tapir.tests.{Fruit, FruitAmount}
import sttp.tapir.{CodecFormat, endpoint, webSocketBody}
import sttp.tapir.json.circe._

import scala.io.Source

class VerifyAsyncAPIYamlTest extends AnyFunSuite with Matchers {

  test("should support basic json websockets") {
    val e = endpoint.in("fruit").out(webSocketBody[Fruit, CodecFormat.Json, Fruit, CodecFormat.Json](AkkaStreams))

    val expectedYaml = loadYaml("expected_json_json.yml")

    val actualYaml = e.toAsyncAPI("The fruit basket", "0.1").toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should support basic string/json websockets") {
    val e = endpoint.in("fruit").out(webSocketBody[String, CodecFormat.TextPlain, Fruit, CodecFormat.Json](AkkaStreams))

    val expectedYaml = loadYaml("expected_string_json.yml")

    val actualYaml = e.toAsyncAPI("The fruit basket", "0.1").toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should support multiple websocket endpoints") {
    val e1 = endpoint.in("stringify").out(webSocketBody[Int, CodecFormat.TextPlain, String, CodecFormat.TextPlain](AkkaStreams))
    val e2 = endpoint.in("pack").out(webSocketBody[Fruit, CodecFormat.Json, FruitAmount, CodecFormat.Json](AkkaStreams))

    val expectedYaml = loadYaml("expected_two_endpoints.yml")

    val actualYaml = List(e1, e2).toAsyncAPI("The fruit basket", "0.1").toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  private def loadYaml(fileName: String): String = {
    noIndentation(Source.fromInputStream(getClass.getResourceAsStream(s"/$fileName")).getLines().mkString("\n"))
  }

  private def noIndentation(s: String) = s.replaceAll("[ \t]", "").trim
}
