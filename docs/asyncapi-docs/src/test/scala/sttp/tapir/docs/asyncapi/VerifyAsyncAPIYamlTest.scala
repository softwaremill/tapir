package sttp.tapir.docs.asyncapi

import io.circe.generic.auto._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.capabilities.akka.AkkaStreams
import sttp.model.HeaderNames
import sttp.tapir.asyncapi.Server
import sttp.tapir.asyncapi.circe.yaml.RichAsyncAPI
import sttp.tapir.tests.{Fruit, FruitAmount}
import sttp.tapir.{CodecFormat, auth, endpoint, header, query, webSocketBody}
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

  test("should support complex bindings") {
    val e = endpoint.post
      .in("fruit")
      .in(header[String](HeaderNames.Authorization))
      .in(query[String]("multiplier"))
      .out(webSocketBody[Fruit, CodecFormat.Json, Fruit, CodecFormat.Json](AkkaStreams))

    val expectedYaml = loadYaml("expected_binding.yml")

    val actualYaml = e.toAsyncAPI("The fruit basket", "0.1").toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should support server security") {
    val e = endpoint.post
      .in("fruit")
      .in(auth.apiKey(header[String](HeaderNames.Authorization)))
      .out(webSocketBody[Fruit, CodecFormat.Json, Fruit, CodecFormat.Json](AkkaStreams))

    val expectedYaml = loadYaml("expected_security.yml")

    val actualYaml = e.toAsyncAPI("The fruit basket", "0.1", List("production" -> Server("example.org", "ws"))).toYaml
    println(actualYaml)
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  private def loadYaml(fileName: String): String = {
    noIndentation(Source.fromInputStream(getClass.getResourceAsStream(s"/$fileName")).getLines().mkString("\n"))
  }

  private def noIndentation(s: String) = s.replaceAll("[ \t]", "").trim
}
