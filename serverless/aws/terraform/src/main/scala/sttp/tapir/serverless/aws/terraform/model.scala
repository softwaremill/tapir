package sttp.tapir.serverless.aws.terraform

case class AwsLambdaFunction(functionName: String, handler: String, runtime: String, s3Bucket: String, s3Key: String)
case class TerraformAwsApiGatewayMethod(httpMethod: String, requestParameters: Map[String, Boolean])
