package sttp.tapir.serverless.aws.terraform

import io.circe.Printer
import io.circe.syntax._
import sttp.tapir._
import sttp.tapir.serverless.aws.terraform.AwsTerraformEncoders._

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Paths}

object TerraformExample extends App {

  val eps = List(
    endpoint.in("hello" / path[String]("name")).in(header[String]("MyHeader"))
  )

  implicit val options: AwsTerraformOptions = AwsTerraformOptions(
    awsRegion = "eu-central-1",
    functionName = "Tapir",
    apiGatewayName = "TapirApiGateway",
    functionSource = S3Source("terraform-example-kubinio", "v1.0.0/example.zip", "nodejs10.x", "main.handler")
  )

  val methods: AwsTerraformApiGateway = EndpointsToTerraformConfig(eps)

  val lambdaJson = Printer.spaces2.print(options.asJson)
  val apiGatewayJson = Printer.spaces2.print(methods.asJson)

  Files.write(Paths.get("serverless/aws/terraform/example/lambda.tf.json"), lambdaJson.getBytes(UTF_8))
  Files.write(Paths.get("serverless/aws/terraform/example/api_gateway.tf.json"), apiGatewayJson.getBytes(UTF_8))
}
