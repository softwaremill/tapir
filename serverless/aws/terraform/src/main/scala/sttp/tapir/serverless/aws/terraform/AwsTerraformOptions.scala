package sttp.tapir.serverless.aws.terraform

import sttp.tapir.serverless.aws.terraform.AwsTerraformOptions.lambdaDefaultAssumeRolePolicy

case class AwsTerraformOptions(
    appName: String,
    awsRegion: String,
    assumeRolePolicy: String = lambdaDefaultAssumeRolePolicy
//    source: FunctionSource,
//    timeout: FiniteDuration = 10.seconds,
//    memorySize: Int = 256
)

object AwsTerraformOptions {
  // grants no policies for lambda function - it cannot access any other AWS services
  private val lambdaDefaultAssumeRolePolicy =
    "{\n  \"Version\": \"2012-10-17\",\n  \"Statement\": [\n    {\n      \"Action\": \"sts:AssumeRole\",\n      \"Principal\": {\n        \"Service\": \"lambda.amazonaws.com\"\n      },\n      \"Effect\": \"Allow\",\n      \"Sid\": \"\"\n    }\n  ]\n}"
}

trait FunctionSource
case class ImageSource(imageUri: String) extends FunctionSource
case class CodeSource(runtime: String, codeUri: String, handler: String) extends FunctionSource
