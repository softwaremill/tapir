package sttp.tapir.serverless.aws.terraform

import io.circe.Json
import io.circe.literal._
import sttp.tapir.serverless.aws.terraform.AwsTerraformOptions.lambdaDefaultAssumeRolePolicy

import scala.concurrent.duration.{DurationInt, FiniteDuration}

case class AwsTerraformOptions(
    awsRegion: String,
    functionName: String,
    apiGatewayName: String,
    apiGatewayDescription: String = "Serverless Application",
    assumeRolePolicy: Json = lambdaDefaultAssumeRolePolicy,
    functionSource: FunctionSource,
    timeout: FiniteDuration = 10.seconds,
    memorySize: Int = 256
)

object AwsTerraformOptions {
  // grants no policies for lambda function - it cannot access any other AWS services
  private val lambdaDefaultAssumeRolePolicy =
    json"""
      {
        "Version": "2012-10-17",
        "Statement": [
          {
            "Action": "sts:AssumeRole",
            "Principal": {
              "Service": "lambda.amazonaws.com"
            },
            "Effect": "Allow",
            "Sid": ""
          }
        ]
      }
    """
}

sealed trait FunctionSource
case class S3Source(bucket: String, key: String, runtime: String, handler: String) extends FunctionSource
case class ImageSource(imageUri: String) extends FunctionSource
case class CodeSource(fileName: String, runtime: String, handler: String) extends FunctionSource
