package sttp.tapir.serverless.aws.examples

import sttp.tapir.serverless.aws.examples.LambdaApiExample.helloEndpoint
import sttp.tapir.serverless.aws.terraform.{AwsTerraformApiGateway, AwsTerraformInterpreter, AwsTerraformOptions, S3Source}

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Paths}
import scala.concurrent.duration.DurationInt

/** Before running the actual example we need to interpret our api as Terraform resources */
object TerraformConfigExample extends App {

  if (args.length != 3) sys.error("Usage: [aws region] [s3 bucket] [s3 key]")

  val region = args(0)
  val bucket = args(1)
  val key = args(2)

  val terraformOptions: AwsTerraformOptions = AwsTerraformOptions(
    region,
    functionName = "PersonsFunction",
    apiGatewayName = "PersonsApiGateway",
    autoDeploy = true,
    functionSource = S3Source(bucket, key, "java11", "sttp.tapir.serverless.aws.examples.LambdaApiExample::handleRequest"),
    timeout = 30.seconds,
    memorySize = 1024
  )

  val apiGateway: AwsTerraformApiGateway = AwsTerraformInterpreter().toTerraformConfig(helloEndpoint)
  val apiGatewayConfig = apiGateway.toJson(terraformOptions)

  Files.write(Paths.get("api_gateway.tf.json"), apiGatewayConfig.getBytes(UTF_8))
}
