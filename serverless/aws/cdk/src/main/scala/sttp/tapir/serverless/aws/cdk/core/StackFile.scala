package sttp.tapir.serverless.aws.cdk.core

case class StackFile(
    apiName: String,
    lambdaName: String,
    runtime: String,
    jarPath: String,
    handler: String,
    timeout: Int,
    memorySize: Int
)
