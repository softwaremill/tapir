package sttp.tapir.serverless.aws.terraform

case class AwsTerraformApiGatewayMethod(httpMethod: String, requestParameters: Map[String, Boolean])
