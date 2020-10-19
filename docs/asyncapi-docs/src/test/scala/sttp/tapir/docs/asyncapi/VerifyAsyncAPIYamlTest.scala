package sttp.tapir.docs.asyncapi

import io.circe.generic.auto._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.capabilities.akka.AkkaStreams
import sttp.tapir.asyncapi.circe.yaml.RichAsyncAPI
import sttp.tapir.tests.Fruit
import sttp.tapir.{CodecFormat, endpoint, webSocketBody}
import sttp.tapir.json.circe._

import scala.io.Source

class VerifyAsyncAPIYamlTest extends AnyFunSuite with Matchers {

  test("should support basic json websockets") {
    val e = endpoint.in("fruit").out(webSocketBody[Fruit, Fruit, CodecFormat.Json](AkkaStreams))

    val expectedYaml = loadYaml("expected.yml")

    val actualYaml = e.toAsyncAPI("The fruit basket", "0.1").toYaml
    println(actualYaml)
    val actualYamlNoIndent = noIndentation(actualYaml)

    actualYamlNoIndent shouldBe expectedYaml
  }

  private def loadYaml(fileName: String): String = {
    noIndentation(Source.fromInputStream(getClass.getResourceAsStream(s"/$fileName")).getLines().mkString("\n"))
  }

  private def noIndentation(s: String) = s.replaceAll("[ \t]", "").trim
}
