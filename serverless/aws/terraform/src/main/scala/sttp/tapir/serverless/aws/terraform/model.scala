package sttp.tapir.serverless.aws.terraform
import io.circe.Printer
import io.circe.syntax._
import sttp.model.Method
import sttp.tapir.serverless.aws.terraform.AwsTerraformEncoders._

case class AwsTerraformApiGateway(resourceTree: ResourceTree, methods: Seq[AwsTerraformApiGatewayMethod]) {
  def toJson()(implicit options: AwsTerraformOptions): String = {
    val gateway = this
    Printer.spaces2.print(gateway.asJson(AwsTerraformEncoders.encoderAwsTerraformApiGateway))
  }
}

case class AwsTerraformApiGatewayMethod(
    name: String,
    path: String,
    httpMethod: Method,
    paths: Seq[PathComponent],
    requestParameters: Seq[(String, Boolean)]
)
