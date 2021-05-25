package sttp.tapir.serverless.aws.examples

import io.circe.Printer
import io.circe.syntax._
import sttp.tapir.serverless.aws.examples.LambdaApiExample.helloEndpoint
import sttp.tapir.serverless.aws.terraform.AwsTerraformEncoders._
import sttp.tapir.serverless.aws.terraform.{AwsTerraformApiGateway, AwsTerraformInterpreter, AwsTerraformOptions, S3Source}

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Paths}

/** Before running the actual example we need to interpret our api as Terraform resources */
object TerraformConfigExample extends App {

  val jarPath = Paths.get("serverless/aws/examples/target/jvm-2.13/tapir-aws-examples.jar").toAbsolutePath.toString

  implicit val terraformOptions: AwsTerraformOptions = AwsTerraformOptions(
    "eu-central-1",
    functionName = "PersonsFunction",
    apiGatewayName = "PersonsApiGateway",
    functionSource = S3Source(
      "terraform-example-kubinio",
      "v1.0.0/tapir-aws-examples.jar",
      "java11",
      "sttp.tapir.serverless.aws.examples.LambdaApiExample::handleRequest"
    )
  )

  val apiGateway: AwsTerraformApiGateway = AwsTerraformInterpreter.toTerraformConfig(helloEndpoint)

  val apiGatewayConfig = Printer.spaces2.print(apiGateway.asJson)

  Files.write(Paths.get("serverless/aws/terraform/example/api_gateway.tf.json"), apiGatewayConfig.getBytes(UTF_8))
}
