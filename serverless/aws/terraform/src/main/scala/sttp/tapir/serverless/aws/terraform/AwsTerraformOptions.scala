package sttp.tapir.serverless.aws.terraform

import sttp.tapir.serverless.aws.terraform.AwsTerraformOptions.lambdaDefaultAssumeRolePolicy

import scala.concurrent.duration.{DurationInt, FiniteDuration}

case class AwsTerraformOptions(
    awsRegion: String,
    lambdaFunctionName: String,
    apiGatewayName: String,
    assumeRolePolicy: String = lambdaDefaultAssumeRolePolicy,
    lambdaSource: FunctionSource,
    timeout: FiniteDuration = 10.seconds,
    memorySize: Int = 256
)

object AwsTerraformOptions {
  // grants no policies for lambda function - it cannot access any other AWS services
  private val lambdaDefaultAssumeRolePolicy =
    "{\n  \"Version\": \"2012-10-17\",\n  \"Statement\": [\n    {\n      \"Action\": \"sts:AssumeRole\",\n      \"Principal\": {\n        \"Service\": \"lambda.amazonaws.com\"\n      },\n      \"Effect\": \"Allow\",\n      \"Sid\": \"\"\n    }\n  ]\n}"
}

sealed trait FunctionSource
case class S3Source(bucket: String, key: String, runtime: String, handler: String) extends FunctionSource
case class ImageSource(imageUri: String) extends FunctionSource
case class CodeSource(fileName: String, runtime: String, handler: String) extends FunctionSource
