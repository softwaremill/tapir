package sttp.tapir.serverless.aws.terraform

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.tapir._
import sttp.tapir.serverless.aws.terraform.VerifyTerraformTemplateTest.{load, noIndentation}

import scala.io.Source

class VerifyTerraformTemplateTest extends AnyFunSuite with Matchers {

  private val options: AwsTerraformOptions = AwsTerraformOptions(
    awsRegion = "eu-central-1",
    functionName = "Tapir",
    apiGatewayName = "TapirApiGateway",
    functionSource = S3Source("bucket", "key", "java11", "Handler::handleRequest")
  )

  test("should handle empty endpoint list") {
    AwsTerraformInterpreter().toTerraformConfig(List.empty).toJson(options)
  }

  test("should match expected json root endpoint") {
    val ep = endpoint

    val expectedJson = load("root_endpoint.json")
    val actualJson = AwsTerraformInterpreter().toTerraformConfig(List(ep)).toJson(options)

    expectedJson shouldBe noIndentation(actualJson)
  }

  test("should match expected json simple endpoint") {
    val ep = endpoint.get.in("hello" / "world")

    val expectedJson = load("simple_endpoint.json")
    val actualJson = AwsTerraformInterpreter().toTerraformConfig(List(ep)).toJson(options)

    expectedJson shouldBe noIndentation(actualJson)
  }

  test("should match expected json endpoint with params") {
    val ep = endpoint.get
      .in("accounts" / path[String]("id") / "history")
      .in(query[Int]("limit"))
      .in(header[String]("X-Account"))
      .in(header[String]("X-Secret"))

    val expectedJson = load("endpoint_with_params.json")
    val actualJson = AwsTerraformInterpreter().toTerraformConfig(List(ep)).toJson(options)

    expectedJson shouldBe noIndentation(actualJson)
  }

  test("should match expected json endpoints with common path") {
    val eps = List(
      endpoint.get.in("accounts" / path[String]("id")),
      endpoint.post.in("accounts"),
      endpoint.get.in("accounts" / path[String]("id") / "transactions"),
      endpoint.post.in("accounts" / path[String]("id") / "transactions")
    )

    val expectedJson = load("endpoints_common_paths.json")
    val actualJson = AwsTerraformInterpreter().toTerraformConfig(eps).toJson(options)

    expectedJson shouldBe noIndentation(actualJson)
  }
}

object VerifyTerraformTemplateTest {

  def load(fileName: String): String = {
    noIndentation(Source.fromInputStream(getClass.getResourceAsStream(s"/$fileName")).getLines().mkString("\n"))
  }

  def noIndentation(s: String): String = s.replaceAll("[ \t]", "").trim
}
