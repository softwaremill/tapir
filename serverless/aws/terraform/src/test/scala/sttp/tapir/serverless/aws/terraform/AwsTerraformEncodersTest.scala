package sttp.tapir.serverless.aws.terraform

import io.circe.Printer
import io.circe.syntax._
import org.scalatest.funsuite.AnyFunSuite
import sttp.tapir.serverless.aws.terraform.AwsTerraformEncoders._

class AwsTerraformEncodersTest extends AnyFunSuite {

  test("hello") {
    val lambda = AwsLambdaFunction("hehe", "hehe", "hehe", "hehe", "hehe")
    implicit val options: AwsTerraformOptions = AwsTerraformOptions("hehe", "hehe")

    val x = lambda.asJson

    val s = Printer.noSpaces.print(x)

    println(s)
  }

  test("hello 2") {
    val methods = List.empty[TerraformAwsApiGatewayMethod]
    implicit val options: AwsTerraformOptions = AwsTerraformOptions("tapir", "eu-west-1")

    println(Printer.noSpaces.print(methods.asJson))
  }
}
