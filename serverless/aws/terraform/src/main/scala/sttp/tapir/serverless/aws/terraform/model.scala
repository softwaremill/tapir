package sttp.tapir.serverless.aws.terraform
import io.circe.Printer
import io.circe.syntax._
import sttp.model.Method
import sttp.tapir.serverless.aws.terraform.AwsTerraformEncoders._

case class AwsTerraformApiGateway(routes: Seq[AwsApiGatewayRoute]) {
  def toJson()(implicit options: AwsTerraformOptions): String = {
    val gateway = this
    Printer.spaces2.print(gateway.asJson)
  }
}

case class AwsApiGatewayRoute(
    name: String,
    path: String,
    httpMethod: Method,
    requestParameters: Seq[(String, String)]
)
