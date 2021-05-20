package sttp.tapir.serverless.aws.terraform

import io.circe.Printer
import io.circe.syntax._
import sttp.tapir.serverless.aws.terraform.AwsTerraformEncoders._

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Paths}

object TerraformExample extends App {

  val lambda = AwsLambdaFunction("ServerlessExample", "main.handler", "nodejs10.x", "terraform-example-kubinio", "v1.0.0/example.zip")
  val methods = List.empty[TerraformAwsApiGatewayMethod]

  implicit val options: AwsTerraformOptions = AwsTerraformOptions("Tapir", "eu-central-1")

  val lambdaJson = Printer.spaces2.print(lambda.asJson)
  val apiGatewayJson = Printer.spaces2.print(methods.asJson)

  Files.write(Paths.get("serverless/aws/terraform/example/lambda.tf.json"), lambdaJson.getBytes(UTF_8))
//  Files.write(Paths.get("serverless/aws/terraform/example/api_gateway.json"), apiGatewayJson.getBytes(UTF_8))
}
