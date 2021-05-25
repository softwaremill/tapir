package sttp.tapir.serverless.aws.terraform

import io.circe.Printer
import io.circe.syntax._
import sttp.tapir._
import sttp.tapir.serverless.aws.terraform.AwsTerraformEncoders._

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Paths}

object TerraformExample extends App {

  val eps = List(
    endpoint.in("accounts" / path[String]("id") / "transactions"),
    endpoint.in("accounts" / path[String]("id") / "history"),
    endpoint.in("accounts" / path[String]("id") / "credit"),
    endpoint.in("accounts" / path[String]("id") / "info")
  )

  implicit val options: AwsTerraformOptions = AwsTerraformOptions(
    awsRegion = "eu-central-1",
    functionName = "Tapir",
    apiGatewayName = "TapirApiGateway",
    functionSource = S3Source("terraform-example-kubinio", "v1.0.0/example.zip", "nodejs10.x", "main.handler")
  )

  val methods: AwsTerraformApiGateway = EndpointsToTerraformConfig(eps)

  val apiGatewayJson = Printer.spaces2.print(methods.asJson)

  Files.write(Paths.get("serverless/aws/terraform/example/api_gateway.tf.json"), apiGatewayJson.getBytes(UTF_8))
}
