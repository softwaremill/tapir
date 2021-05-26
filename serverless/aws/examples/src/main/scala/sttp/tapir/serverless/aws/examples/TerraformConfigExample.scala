package sttp.tapir.serverless.aws.examples

import io.circe.Printer
import io.circe.syntax._
import sttp.tapir.serverless.aws.examples.LambdaApiExample.helloEndpoint
import sttp.tapir.serverless.aws.terraform.AwsTerraformEncoders._
import sttp.tapir.serverless.aws.terraform.{AwsTerraformApiGateway, AwsTerraformInterpreter, AwsTerraformOptions, S3Source}

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Paths}
import scala.concurrent.duration.DurationInt

/** Before running the actual example we need to interpret our api as Terraform resources */
object TerraformConfigExample extends App {

  implicit val terraformOptions: AwsTerraformOptions = AwsTerraformOptions(
    "eu-central-1",
    functionName = "PersonsFunction",
    apiGatewayName = "PersonsApiGateway",
    autoDeploy = true,
    functionSource = S3Source(
      "terraform-example-kubinio",
      "v1.0.0/tapir-aws-examples.jar",
      "java11",
      "sttp.tapir.serverless.aws.examples.LambdaApiExample::handleRequest"
    ),
    timeout = 30.seconds,
    memorySize = 1024
  )

  val apiGateway: AwsTerraformApiGateway = AwsTerraformInterpreter.toTerraformConfig(helloEndpoint)

  val apiGatewayConfig = Printer.spaces2.print(apiGateway.asJson)

  Files.write(Paths.get("serverless/aws/terraform/example/api_gateway.tf.json"), apiGatewayConfig.getBytes(UTF_8))
}
