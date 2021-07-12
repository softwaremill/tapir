package sttp.tapir.serverless.aws.terraform

import io.circe.Printer
import io.circe.syntax._
import sttp.model.Method

case class AwsTerraformApiGateway(routes: Seq[AwsApiGatewayRoute]) {
  def toJson(options: AwsTerraformOptions): String = {
    val encoders = AwsTerraformEncoders(options)
    import encoders._
    Printer.spaces2.print(this.asJson)
  }
}

case class AwsApiGatewayRoute(
    name: String,
    path: String,
    httpMethod: Method
)
