package sttp.tapir.docs.asyncapi

import io.circe.generic.auto._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.tapir.generic.auto._
import sttp.capabilities.akka.AkkaStreams
import sttp.model.HeaderNames
import sttp.tapir.Schema.SName
import sttp.tapir.asyncapi.{Info, Server}
import sttp.tapir.asyncapi.circe.yaml.RichAsyncAPI
import sttp.tapir.docs.asyncapi.AsyncAPIDocsOptions.defaultOperationIdGenerator
import sttp.tapir.{CodecFormat, auth, endpoint, header, query, webSocketBody}
import sttp.tapir.json.circe._
import sttp.tapir._
import sttp.tapir.tests.data.{Fruit, FruitAmount}

import scala.io.Source

class VerifyAsyncAPIYamlTest extends AnyFunSuite with Matchers {

  test("should support basic websocket") {
    val e = endpoint.in("fruit").out(webSocketBody[String, CodecFormat.TextPlain, String, CodecFormat.TextPlain](AkkaStreams))

    val expectedYaml = loadYaml("expected_string.yml")
    val expectedYamlNoIndent = noIndentation(expectedYaml)

    val actualYaml = AsyncAPIInterpreter().toAsyncAPI(e, "The fruit basket", "0.1").toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYamlNoIndent
  }

  test("should support basic json websockets") {
    val e = endpoint.in("fruit").out(webSocketBody[Fruit, CodecFormat.Json, Fruit, CodecFormat.Json](AkkaStreams))

    val expectedYaml = loadYaml("expected_json_json.yml")

    val actualYaml = AsyncAPIInterpreter().toAsyncAPI(e, "The fruit basket", "0.1").toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should support providing custom schema name") {
    val e = endpoint.in("fruit").out(webSocketBody[Fruit, CodecFormat.Json, Fruit, CodecFormat.Json](AkkaStreams))

    def customSchemaName(name: SName) = (name.fullName +: name.typeParameterShortNames).mkString("_")
    val options = AsyncAPIDocsOptions.default.copy(defaultOperationIdGenerator("on"), defaultOperationIdGenerator("send"), customSchemaName)
    val expectedYaml = loadYaml("expected_json_custom_schema_name.yml")

    val actualYaml = AsyncAPIInterpreter(options).toAsyncAPI(e, "The fruit basket", "0.1").toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should support basic string/json websockets") {
    val e = endpoint.in("fruit").out(webSocketBody[String, CodecFormat.TextPlain, Fruit, CodecFormat.Json](AkkaStreams))

    val expectedYaml = loadYaml("expected_string_json.yml")

    val actualYaml = AsyncAPIInterpreter().toAsyncAPI(e, "The fruit basket", "0.1").toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should support multiple websocket endpoints") {
    val e1 = endpoint.in("stringify").out(webSocketBody[Int, CodecFormat.TextPlain, String, CodecFormat.TextPlain](AkkaStreams))
    val e2 = endpoint.in("pack").out(webSocketBody[Fruit, CodecFormat.Json, FruitAmount, CodecFormat.Json](AkkaStreams))

    val expectedYaml = loadYaml("expected_two_endpoints.yml")

    val actualYaml = AsyncAPIInterpreter().toAsyncAPI(List(e1, e2), "The fruit basket", "0.1").toYaml
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

    val actualYaml = AsyncAPIInterpreter().toAsyncAPI(e, "The fruit basket", "0.1").toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should support server security") {
    val e = endpoint.post
      .securityIn(auth.apiKey(header[String](HeaderNames.Authorization)))
      .in("fruit")
      .out(webSocketBody[Fruit, CodecFormat.Json, Fruit, CodecFormat.Json](AkkaStreams))

    val expectedYaml = loadYaml("expected_security.yml")

    val actualYaml =
      AsyncAPIInterpreter().toAsyncAPI(e, "The fruit basket", "0.1", List("production" -> Server("example.org", "ws"))).toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should include examples") {
    val e = endpoint
      .in("fruit")
      .out(
        webSocketBody[Fruit, CodecFormat.Json, Int, CodecFormat.Json](AkkaStreams)
          .requestsExample(Fruit("apple"))
          .responsesExamples(List(10, 42))
      )

    val expectedYaml = loadYaml("expected_json_examples.yml")

    val actualYaml = AsyncAPIInterpreter().toAsyncAPI(e, "The fruit basket", "0.1").toYaml
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  test("should apply asyncapi extensions in correct places") {
    case class MyExtension(string: String, int: Int)

    val sampleEndpoint =
      endpoint.post
        .in("path-hello" / path[String]("world").docsExtension("x-path", 22))
        .out(
          webSocketBody[FruitAmount, CodecFormat.Json, Fruit, CodecFormat.Json](AkkaStreams)
            .requestsDocsExtension("x-request", List("array-0", "array-1"))
            .responsesDocsExtension("x-response", "foo")
        )
        .docsExtension("x-endpoint-level-string", "world")
        .docsExtension("x-endpoint-level-int", 11)
        .docsExtension("x-endpoint-obj", MyExtension("42.42", 42))

    val rootExtensions = List(
      DocsExtension.of("x-root-bool", true),
      DocsExtension.of("x-root-list", List(1, 2, 4))
    )

    val actualYaml = AsyncAPIInterpreter().toAsyncAPI(sampleEndpoint, Info("title", "1.0"), Seq.empty, rootExtensions).toYaml

    noIndentation(actualYaml) shouldBe loadYaml("expected_extensions.yml")
  }

  private def loadYaml(fileName: String): String = {
    noIndentation(Source.fromInputStream(getClass.getResourceAsStream(s"/$fileName")).getLines().mkString("\n"))
  }

  private def noIndentation(s: String) = s.replaceAll("[ \t]", "").trim
}
