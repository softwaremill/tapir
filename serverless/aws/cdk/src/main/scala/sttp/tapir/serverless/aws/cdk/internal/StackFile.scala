package sttp.tapir.serverless.aws.cdk.internal

import sttp.tapir.serverless.aws.cdk.AwsCdkOptions
import sttp.tapir.serverless.aws.cdk.AwsCdkOptions.Runtime

case class StackFile(
    apiName: String,
    lambdaName: String,
    runtime: String,
    jarPath: String,
    handler: String,
    timeout: Long,
    memorySize: Int
) {
  // productElementNames does not work with Scala 2.12
  def getFields: List[String] = List(
    "apiName",
    "lambdaName",
    "runtime",
    "jarPath",
    "handler",
    "timeout",
    "memorySize"
  )

  // productElementNames does not work with Scala 2.12
  def getValue(field: String): String = field match {
    case "apiName"    => apiName
    case "lambdaName" => lambdaName
    case "runtime"    => runtime
    case "jarPath"    => jarPath
    case "handler"    => handler
    case "timeout"    => timeout.toString
    case "memorySize" => memorySize.toString
    case _            => ""
  }
}

object StackFile {
  private val tsRuntimePackage: String = "lambda.Runtime"

  def fromAwsCdkOptions(options: AwsCdkOptions): StackFile =
    StackFile(
      apiName = options.apiName,
      lambdaName = options.lambdaName,
      runtime = options.runtime match {
        case Runtime.Java8         => s"$tsRuntimePackage.JAVA_8"
        case Runtime.Java8Corretto => s"$tsRuntimePackage.JAVA_8_CORRETTO"
        case Runtime.Java11        => s"$tsRuntimePackage.JAVA_11"
      },
      jarPath = options.codeUri,
      handler = options.handler,
      timeout = options.timeout.toSeconds,
      memorySize = options.memorySizeInMB
    )
}
