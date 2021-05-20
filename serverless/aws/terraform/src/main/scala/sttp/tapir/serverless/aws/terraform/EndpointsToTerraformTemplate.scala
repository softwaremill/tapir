package sttp.tapir.serverless.aws.terraform

import io.circe.Json
import sttp.tapir.Endpoint

private[terraform] object EndpointsToTerraformTemplate {
  def apply(es: List[Endpoint[_, _, _, _]])(implicit options: AwsTerraformOptions): Unit = {
    // first create lambda.tf



    // then api_gateway.tf
  }
}
